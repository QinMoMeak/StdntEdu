package com.stdntedu.transfer.exporttask;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.extraction.entity.AttachmentEntity;
import com.stdntedu.ai.extraction.mapper.AttachmentMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.ExportCreateRequest;
import com.stdntedu.generated.model.ExportFormat;
import com.stdntedu.generated.model.ExportTask;
import com.stdntedu.generated.model.ExportTaskPageResponseAllOfData;
import com.stdntedu.generated.model.ExportTaskStatus;
import com.stdntedu.generated.model.ExportType;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import com.stdntedu.student.mapper.StudentMapper;
import com.stdntedu.transfer.entity.ExportTaskEntity;
import com.stdntedu.transfer.mapper.ExportTaskMapper;
import com.stdntedu.transfer.service.TransferDispatcher;
import com.stdntedu.transfer.service.TransferFileService;
import com.stdntedu.transfer.service.TransferFileService.Download;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ExportTaskService {
    private static final Set<ExportType> GLOBAL = Set.of(ExportType.KNOWLEDGE, ExportType.LEARNING_RESOURCE);
    private final ExportTaskMapper tasks;
    private final AttachmentMapper attachments;
    private final TransferFileService files;
    private final TransferDispatcher dispatcher;
    private final StudentMapper students;
    private final ObjectMapper json;
    private final IdConverter ids;
    private final SystemTimezoneProvider time;

    public ExportTaskService(ExportTaskMapper tasks, AttachmentMapper attachments, TransferFileService files,
            TransferDispatcher dispatcher, StudentMapper students, ObjectMapper json, IdConverter ids,
            SystemTimezoneProvider time) {
        this.tasks = tasks;
        this.attachments = attachments;
        this.files = files;
        this.dispatcher = dispatcher;
        this.students = students;
        this.json = json;
        this.ids = ids;
        this.time = time;
    }

    @Transactional
    public ExportTask create(ExportCreateRequest request) {
        List<ExportType> types = request.getExportTypes() == null ? null : List.copyOf(request.getExportTypes());
        if (types == null || types.isEmpty()) throw rule("exportTypes is required");
        if (types.contains(ExportType.FULL_DATA) && types.size() != 1) throw rule("FULL_DATA cannot be combined");
        if (request.getFormat() == ExportFormat.CSV
                && (types.size() != 1 || types.contains(ExportType.FULL_DATA))) {
            throw rule("CSV requires one concrete exportType");
        }
        if (Boolean.TRUE.equals(request.getIncludeAttachments())) throw rule("attachment files are not exported");
        if (Boolean.TRUE.equals(request.getIncludeSystemConfig())) throw rule("system_config is not exported");
        validateDateRange(request.getStartDate(), request.getEndDate());
        boolean needsStudent = types.contains(ExportType.FULL_DATA) || types.stream().anyMatch(type -> !GLOBAL.contains(type));
        Long studentId = request.getStudentId() == null ? null : ids.toLong(request.getStudentId());
        if (needsStudent && studentId == null) throw rule("studentId is required for selected export types");
        if (studentId != null && students.selectById(studentId) == null) {
            throw new ResourceNotFoundException("student not found");
        }

        ExportTaskEntity task = new ExportTaskEntity();
        task.setTaskCode("EXP-" + UUID.randomUUID().toString().replace("-", ""));
        task.setStudentId(studentId);
        task.setExportTypesJson(write(types.stream().map(ExportType::getValue).toList()));
        task.setExportFormat(request.getFormat().getValue());
        task.setStatus("PENDING");
        task.setFilterJson(write(new ExportFilter(request.getStartDate(), request.getEndDate(),
                Boolean.TRUE.equals(request.getIncludeAiAnalysis()), request.getTimezone())));
        task.setIncludeAttachments(false);
        task.setIncludeDeleted(Boolean.TRUE.equals(request.getIncludeDeleted()));
        task.setExpireTime(time.localDateTime().plusDays(7));
        if (tasks.insert(task) != 1) throw new IllegalStateException("export task was not created");
        afterCommit(task.getId());
        return get(task.getId().toString());
    }

    @Transactional(readOnly = true)
    public ExportTaskPageResponseAllOfData list(String studentId, ExportTaskStatus status, ExportFormat format,
            OffsetDateTime start, OffsetDateTime end, Integer page, Integer pageSize) {
        int number = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        if (start != null && end != null && !start.isBefore(end)) throw rule("startTime must be before endTime");
        var query = Wrappers.<ExportTaskEntity>lambdaQuery()
                .eq(studentId != null, ExportTaskEntity::getStudentId, studentId == null ? null : ids.toLong(studentId))
                .eq(status != null, ExportTaskEntity::getStatus, status == null ? null : status.getValue())
                .eq(format != null, ExportTaskEntity::getExportFormat, format == null ? null : format.getValue())
                .ge(start != null, ExportTaskEntity::getCreateTime, start == null ? null : time.toLocalDateTime(start))
                .lt(end != null, ExportTaskEntity::getCreateTime, end == null ? null : time.toLocalDateTime(end))
                .orderByDesc(ExportTaskEntity::getCreateTime).orderByDesc(ExportTaskEntity::getId);
        Page<ExportTaskEntity> result = tasks.selectPage(new Page<>(number, size), query);
        Map<Long, AttachmentEntity> metadata = outputMap(result.getRecords());
        return new ExportTaskPageResponseAllOfData(number, size, result.getTotal(), Math.toIntExact(result.getPages()))
                .items(result.getRecords().stream().map(task -> dto(task, task.getOutputAttachmentId() == null
                        ? null : metadata.get(task.getOutputAttachmentId()))).toList());
    }

    @Transactional(readOnly = true)
    public ExportTask get(String taskId) {
        ExportTaskEntity task = require(ids.toLong(taskId));
        AttachmentEntity output = task.getOutputAttachmentId() == null ? null : files.require(task.getOutputAttachmentId());
        return dto(task, output);
    }

    @Transactional
    public ExportTask cancel(String taskId) {
        Long id = ids.toLong(taskId);
        require(id);
        int changed = tasks.update(null, Wrappers.<ExportTaskEntity>lambdaUpdate()
                .eq(ExportTaskEntity::getId, id).in(ExportTaskEntity::getStatus, List.of("PENDING", "RUNNING"))
                .set(ExportTaskEntity::getStatus, "CANCELLED")
                .set(ExportTaskEntity::getFinishedTime, time.localDateTime()));
        if (changed != 1) throw conflict("export task cannot be cancelled in its current state");
        return get(taskId);
    }

    @Transactional(readOnly = true)
    public Download download(String taskId) {
        ExportTaskEntity task = require(ids.toLong(taskId));
        if (!"SUCCESS".equals(task.getStatus()) || task.getOutputAttachmentId() == null) {
            throw conflict("export file is not ready");
        }
        return files.download(task.getOutputAttachmentId());
    }

    public ExportFilter filter(ExportTaskEntity task) {
        try { return json.readValue(task.getFilterJson(), ExportFilter.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("stored export filter is invalid", ex); }
    }

    public List<ExportType> types(ExportTaskEntity task) {
        try {
            List<String> values = json.readValue(task.getExportTypesJson(), new TypeReference<>() { });
            return values.stream().map(ExportType::fromValue).toList();
        } catch (JsonProcessingException ex) { throw new IllegalStateException("stored export types are invalid", ex); }
    }

    private ExportTask dto(ExportTaskEntity task, AttachmentEntity output) {
        ExportTask dto = new ExportTask();
        dto.setTaskId(task.getId().toString());
        dto.setStudentId(task.getStudentId() == null ? null : task.getStudentId().toString());
        dto.setExportTypes(types(task));
        dto.setFormat(ExportFormat.fromValue(task.getExportFormat()));
        dto.setStatus(ExportTaskStatus.fromValue(task.getStatus()));
        dto.setIncludeAttachments(task.getIncludeAttachments());
        dto.setIncludeDeleted(task.getIncludeDeleted());
        if (output != null) {
            dto.setFileName(output.getFileName());
            dto.setFileSize(output.getFileSize());
            dto.setChecksum(output.getSha256());
            dto.setDownloadUrl(URI.create("/api/v1/exports/" + task.getId() + "/download"));
        }
        dto.setProgressPercent(task.getProgressPercent());
        dto.setErrorCode(task.getErrorCode());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setExpiresAt(time.toOffsetDateTime(task.getExpireTime()));
        dto.setStartedAt(time.toOffsetDateTime(task.getStartedTime()));
        dto.setCreatedAt(time.toOffsetDateTime(task.getCreateTime()));
        dto.setUpdatedAt(time.toOffsetDateTime(task.getUpdateTime()));
        dto.setFinishedAt(time.toOffsetDateTime(task.getFinishedTime()));
        return dto;
    }

    private Map<Long, AttachmentEntity> outputMap(List<ExportTaskEntity> rows) {
        List<Long> ids = rows.stream().map(ExportTaskEntity::getOutputAttachmentId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return attachments.selectBatchIds(ids).stream().collect(Collectors.toMap(AttachmentEntity::getId, value -> value));
    }

    private ExportTaskEntity require(Long id) {
        ExportTaskEntity task = tasks.selectById(id);
        if (task == null) throw new ResourceNotFoundException("export task not found");
        return task;
    }

    private void afterCommit(Long id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { dispatcher.dispatchExport(id); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { dispatcher.dispatchExport(id); }
        });
    }

    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) throw rule("endDate must not be before startDate");
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("task input could not be serialized", ex); }
    }

    private BusinessException rule(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
    private BusinessException conflict(String message) {
        return new BusinessException("TASK_STATE_CONFLICT", message, HttpStatus.CONFLICT);
    }

    public record ExportFilter(LocalDate startDate, LocalDate endDate, boolean includeAiAnalysis, String timezone) { }
}
