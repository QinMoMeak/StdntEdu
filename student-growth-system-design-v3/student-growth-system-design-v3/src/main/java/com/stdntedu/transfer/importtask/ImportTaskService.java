package com.stdntedu.transfer.importtask;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
import com.stdntedu.generated.model.DuplicateStrategy;
import com.stdntedu.generated.model.ImportConfirmRequest;
import com.stdntedu.generated.model.ImportPreviewDto;
import com.stdntedu.generated.model.ImportStatus;
import com.stdntedu.generated.model.ImportTaskDto;
import com.stdntedu.generated.model.ImportTaskPageResponseAllOfData;
import com.stdntedu.generated.model.ImportType;
import com.stdntedu.generated.model.RetryImportRequest;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import com.stdntedu.student.mapper.StudentMapper;
import com.stdntedu.transfer.entity.ImportTaskEntity;
import com.stdntedu.transfer.mapper.ImportTaskMapper;
import com.stdntedu.transfer.service.TransferDispatcher;
import com.stdntedu.transfer.service.TransferFileService;
import com.stdntedu.transfer.service.TransferFileService.Download;
import com.stdntedu.transfer.service.TransferFileService.StoredAttachment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportTaskService {
    private static final Set<String> CANCELLABLE = Set.of(
            "UPLOADED", "VALIDATING", "PREVIEW_READY", "CONFIRM_PENDING", "IMPORTING");
    private final ImportTaskMapper tasks;
    private final AttachmentMapper attachments;
    private final TransferFileService files;
    private final TransferDispatcher dispatcher;
    private final StudentMapper students;
    private final ObjectMapper json;
    private final IdConverter ids;
    private final SystemTimezoneProvider time;

    public ImportTaskService(ImportTaskMapper tasks, AttachmentMapper attachments, TransferFileService files,
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
    public ImportTaskDto create(MultipartFile upload, ImportType type, String studentId, Boolean dryRun,
            String encoding, String sheetName, Boolean hasHeader) {
        Long owner = validateScope(type, studentId);
        if (Boolean.FALSE.equals(dryRun) || !"UTF-8".equalsIgnoreCase(encoding)
                || Boolean.FALSE.equals(hasHeader)) throw rule("only preview-first UTF-8 imports with headers are supported");
        StoredAttachment stored = files.storeUpload(upload);
        ImportTaskEntity task = new ImportTaskEntity();
        task.setTaskCode("IMP-" + UUID.randomUUID().toString().replace("-", ""));
        task.setImportType(type.getValue());
        task.setStudentId(owner);
        task.setStatus("UPLOADED");
        task.setAttachmentId(stored.entity().getId());
        task.setOptionsJson(write(Map.of(
                "dryRun", true, "encoding", "UTF-8", "sheetName", sheetName == null ? "" : sheetName,
                "hasHeader", true, "fileName", stored.entity().getFileName())));
        task.setExpireTime(time.localDateTime().plusDays(7));
        try {
            if (tasks.insert(task) != 1) throw new IllegalStateException("import task was not created");
        } catch (RuntimeException ex) {
            files.cleanup(stored);
            throw ex;
        }
        afterCommit(task.getId());
        return get(task.getId().toString());
    }

    @Transactional(readOnly = true)
    public ImportTaskPageResponseAllOfData list(ImportType type, ImportStatus status, String studentId,
            OffsetDateTime start, OffsetDateTime end, Integer page, Integer pageSize) {
        int number = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        validateRange(start, end);
        var query = Wrappers.<ImportTaskEntity>lambdaQuery()
                .eq(type != null, ImportTaskEntity::getImportType, type == null ? null : type.getValue())
                .eq(status != null, ImportTaskEntity::getStatus, status == null ? null : status.getValue())
                .eq(studentId != null, ImportTaskEntity::getStudentId, studentId == null ? null : ids.toLong(studentId))
                .ge(start != null, ImportTaskEntity::getCreateTime, start == null ? null : time.toLocalDateTime(start))
                .lt(end != null, ImportTaskEntity::getCreateTime, end == null ? null : time.toLocalDateTime(end))
                .orderByDesc(ImportTaskEntity::getCreateTime).orderByDesc(ImportTaskEntity::getId);
        Page<ImportTaskEntity> result = tasks.selectPage(new Page<>(number, size), query);
        Map<Long, AttachmentEntity> metadata = attachmentMap(result.getRecords());
        return new ImportTaskPageResponseAllOfData(number, size, result.getTotal(), Math.toIntExact(result.getPages()))
                .items(result.getRecords().stream().map(task -> dto(task, metadata.get(task.getAttachmentId()))).toList());
    }

    @Transactional(readOnly = true)
    public ImportTaskDto get(String taskId) {
        ImportTaskEntity task = require(ids.toLong(taskId));
        return dto(task, files.require(task.getAttachmentId()));
    }

    @Transactional
    public ImportTaskDto confirm(String taskId, String idempotencyKey, ImportConfirmRequest request) {
        validateKey(idempotencyKey);
        NormalizedConfirm normalized = normalize(request);
        String canonical = write(normalized);
        String hash = sha256(canonical);
        Long id = ids.toLong(taskId);
        ImportTaskEntity replay = tasks.selectOne(Wrappers.<ImportTaskEntity>lambdaQuery()
                .eq(ImportTaskEntity::getIdempotencyKey, idempotencyKey));
        if (replay != null) return replay(replay, id, hash);
        ImportTaskEntity task = require(id);
        if (!"PREVIEW_READY".equals(task.getStatus())) throw conflict("import task is not ready for confirmation");
        if (task.getInvalidRows() > 0 && !normalized.skipInvalidRows()) {
            throw rule("invalid rows must be resolved or explicitly skipped");
        }
        try {
            int changed = tasks.update(null, Wrappers.<ImportTaskEntity>lambdaUpdate()
                    .eq(ImportTaskEntity::getId, id).eq(ImportTaskEntity::getStatus, "PREVIEW_READY")
                    .set(ImportTaskEntity::getStatus, "CONFIRM_PENDING")
                    .set(ImportTaskEntity::getIdempotencyKey, idempotencyKey)
                    .set(ImportTaskEntity::getConfirmRequestJson, canonical)
                    .set(ImportTaskEntity::getConfirmRequestHash, hash)
                    .set(ImportTaskEntity::getProgressPercent, 0));
            if (changed != 1) throw conflict("import task state changed");
        } catch (DuplicateKeyException ex) {
            ImportTaskEntity existing = tasks.selectOne(Wrappers.<ImportTaskEntity>lambdaQuery()
                    .eq(ImportTaskEntity::getIdempotencyKey, idempotencyKey));
            if (existing != null) return replay(existing, id, hash);
            throw ex;
        }
        afterCommit(id);
        return get(taskId);
    }

    @Transactional
    public ImportTaskDto cancel(String taskId) {
        Long id = ids.toLong(taskId);
        require(id);
        int changed = tasks.update(null, Wrappers.<ImportTaskEntity>lambdaUpdate()
                .eq(ImportTaskEntity::getId, id).in(ImportTaskEntity::getStatus, CANCELLABLE)
                .set(ImportTaskEntity::getStatus, "CANCELLED")
                .set(ImportTaskEntity::getFinishedTime, time.localDateTime()));
        if (changed != 1) throw conflict("import task cannot be cancelled in its current state");
        return get(taskId);
    }

    @Transactional
    public ImportTaskDto retry(String taskId, RetryImportRequest request) {
        if (request.getDuplicateStrategy() != null && request.getDuplicateStrategy() != DuplicateStrategy.REJECT) {
            throw rule("only REJECT duplicate strategy is supported");
        }
        Long id = ids.toLong(taskId);
        ImportTaskEntity current = require(id);
        if (!"FAILED".equals(current.getStatus()) || current.getRetryCount() >= 3) {
            throw conflict("import task is not retryable");
        }
        int changed = tasks.update(null, Wrappers.<ImportTaskEntity>lambdaUpdate()
                .eq(ImportTaskEntity::getId, id).eq(ImportTaskEntity::getStatus, "FAILED")
                .eq(ImportTaskEntity::getRetryCount, current.getRetryCount())
                .set(ImportTaskEntity::getStatus, "UPLOADED")
                .set(ImportTaskEntity::getRetryCount, current.getRetryCount() + 1)
                .set(ImportTaskEntity::getIdempotencyKey, null)
                .set(ImportTaskEntity::getConfirmRequestJson, null)
                .set(ImportTaskEntity::getConfirmRequestHash, null)
                .set(ImportTaskEntity::getErrorCode, null).set(ImportTaskEntity::getErrorMessage, null)
                .set(ImportTaskEntity::getStartedTime, null).set(ImportTaskEntity::getFinishedTime, null)
                .set(ImportTaskEntity::getProgressPercent, 0));
        if (changed != 1) throw conflict("import task state changed");
        afterCommit(id);
        return get(taskId);
    }

    @Transactional(readOnly = true)
    public Download errorReport(String taskId) {
        ImportTaskEntity task = require(ids.toLong(taskId));
        if (task.getErrorReportAttachmentId() == null) throw new ResourceNotFoundException("error report not found");
        return files.download(task.getErrorReportAttachmentId());
    }

    private ImportTaskDto replay(ImportTaskEntity existing, Long taskId, String hash) {
        if (!existing.getId().equals(taskId) || !hash.equals(existing.getConfirmRequestHash())) {
            throw new BusinessException("IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key was already used for a different request", HttpStatus.CONFLICT);
        }
        return dto(existing, files.require(existing.getAttachmentId()));
    }

    private NormalizedConfirm normalize(ImportConfirmRequest request) {
        boolean atomic = !Boolean.FALSE.equals(request.getAtomic());
        boolean skip = Boolean.TRUE.equals(request.getSkipInvalidRows());
        if (!atomic && !skip) throw rule("atomic=false requires skipInvalidRows=true");
        if (request.getDuplicateStrategy() != null && request.getDuplicateStrategy() != DuplicateStrategy.REJECT) {
            throw rule("only REJECT duplicate strategy is supported");
        }
        List<Integer> selected = request.getSelectedRows() == null ? List.of()
                : request.getSelectedRows().stream().sorted().toList();
        return new NormalizedConfirm(atomic, skip, "REJECT", selected,
                Boolean.TRUE.equals(request.getConfirmWarnings()));
    }

    private Long validateScope(ImportType type, String studentId) {
        boolean privateType = type == ImportType.SCORE || type == ImportType.WRONG_QUESTION;
        if (privateType && studentId == null) throw rule("studentId is required for this import type");
        if (!privateType && studentId != null) throw rule("studentId is not allowed for this import type");
        if (studentId == null) return null;
        Long id = ids.toLong(studentId);
        if (students.selectById(id) == null) throw new ResourceNotFoundException("student not found");
        return id;
    }

    private ImportTaskDto dto(ImportTaskEntity task, AttachmentEntity attachment) {
        ImportTaskDto dto = new ImportTaskDto();
        dto.setTaskId(task.getId().toString());
        dto.setImportType(ImportType.fromValue(task.getImportType()));
        dto.setStudentId(task.getStudentId() == null ? null : task.getStudentId().toString());
        dto.setStatus(ImportStatus.fromValue(task.getStatus()));
        dto.setFileName(attachment.getFileName());
        dto.setFileSize(attachment.getFileSize());
        dto.setInputFileCount(task.getInputFileCount());
        dto.setTotalRows(task.getTotalRows());
        dto.setValidRows(task.getValidRows());
        dto.setInvalidRows(task.getInvalidRows());
        dto.setWarningRows(task.getWarningRows());
        dto.setImportedRows(task.getImportedRows());
        dto.setSkippedRows(task.getSkippedRows());
        dto.setFailedRows(task.getFailedRows());
        dto.setProgressPercent(task.getProgressPercent());
        dto.setRetryCount(task.getRetryCount());
        dto.setPreview(readPreview(task.getPreviewJson()));
        dto.setErrorReportAvailable(task.getErrorReportAttachmentId() != null);
        dto.setErrorCode(task.getErrorCode());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setExpiresAt(time.toOffsetDateTime(task.getExpireTime()));
        dto.setStartedAt(time.toOffsetDateTime(task.getStartedTime()));
        dto.setCreatedAt(time.toOffsetDateTime(task.getCreateTime()));
        dto.setUpdatedAt(time.toOffsetDateTime(task.getUpdateTime()));
        dto.setFinishedAt(time.toOffsetDateTime(task.getFinishedTime()));
        return dto;
    }

    private ImportPreviewDto readPreview(String value) {
        if (value == null) return null;
        try { return json.readValue(value, ImportPreviewDto.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("stored import preview is invalid", ex); }
    }

    private Map<Long, AttachmentEntity> attachmentMap(List<ImportTaskEntity> rows) {
        List<Long> attachmentIds = rows.stream().map(ImportTaskEntity::getAttachmentId).distinct().toList();
        if (attachmentIds.isEmpty()) return Map.of();
        return attachments.selectBatchIds(attachmentIds).stream()
                .collect(Collectors.toMap(AttachmentEntity::getId, value -> value));
    }

    private ImportTaskEntity require(Long id) {
        ImportTaskEntity task = tasks.selectById(id);
        if (task == null) throw new ResourceNotFoundException("import task not found");
        return task;
    }

    private void afterCommit(Long id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { dispatcher.dispatchImport(id); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { dispatcher.dispatchImport(id); }
        });
    }

    private void validateRange(OffsetDateTime start, OffsetDateTime end) {
        if (start != null && end != null && !start.isBefore(end)) throw rule("startTime must be before endTime");
    }

    private void validateKey(String key) {
        if (key == null || key.length() < 8 || key.length() > 64) throw rule("Idempotency-Key must contain 8 to 64 characters");
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("task input could not be serialized", ex); }
    }

    private String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private BusinessException rule(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BusinessException conflict(String message) {
        return new BusinessException("TASK_STATE_CONFLICT", message, HttpStatus.CONFLICT);
    }

    public record NormalizedConfirm(boolean atomic, boolean skipInvalidRows, String duplicateStrategy,
            List<Integer> selectedRows, boolean confirmWarnings) { }
}
