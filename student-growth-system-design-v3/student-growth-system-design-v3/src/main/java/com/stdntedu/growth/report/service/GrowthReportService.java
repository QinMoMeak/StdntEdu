package com.stdntedu.growth.report.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.GenerationType;
import com.stdntedu.generated.model.GrowthReportCreateRequest;
import com.stdntedu.generated.model.GrowthReportDto;
import com.stdntedu.generated.model.GrowthReportPageResponseAllOfData;
import com.stdntedu.generated.model.GrowthReportRegenerateRequest;
import com.stdntedu.generated.model.GrowthReportSnapshotDto;
import com.stdntedu.generated.model.GrowthReportStatus;
import com.stdntedu.generated.model.ReportType;
import com.stdntedu.growth.report.entity.GrowthReportEntity;
import com.stdntedu.growth.report.mapper.GrowthReportMapper;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import com.stdntedu.student.mapper.StudentMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GrowthReportService {
    private static final String GENERATION_VERSION = "1.0";
    private final GrowthReportMapper reports;
    private final StudentMapper students;
    private final GrowthReportDispatcher dispatcher;
    private final ObjectMapper json;
    private final IdConverter ids;
    private final SystemTimezoneProvider time;

    public GrowthReportService(GrowthReportMapper reports, StudentMapper students,
            GrowthReportDispatcher dispatcher, ObjectMapper json, IdConverter ids,
            SystemTimezoneProvider time) {
        this.reports = reports;
        this.students = students;
        this.dispatcher = dispatcher;
        this.json = json;
        this.ids = ids;
        this.time = time;
    }

    @Transactional
    public GrowthReportDto create(GrowthReportCreateRequest request) {
        Long studentId = validate(request);
        GrowthReportEntity report = new GrowthReportEntity();
        report.setStudentId(studentId);
        report.setReportType(request.getReportType().getValue());
        report.setTitle(request.getTitle().trim());
        report.setStartDate(request.getStartDate());
        report.setEndDate(request.getEndDate());
        report.setGenerationType("DETERMINISTIC");
        report.setStatus("PENDING");
        report.setRequestJson(write(new CanonicalRequest(1, GENERATION_VERSION, studentId.toString(),
                request.getReportType().getValue(), report.getTitle(), request.getStartDate(),
                request.getEndDate(), null)));
        report.setSnapshotSchemaVersion(1);
        report.setGenerationVersion(GENERATION_VERSION);
        report.setProgressPercent(0);
        report.setCancelRequested(false);
        if (reports.insert(report) != 1) throw new IllegalStateException("growth report was not created");
        afterCommit(report.getId());
        return dto(report);
    }

    @Transactional(readOnly = true)
    public GrowthReportDto get(String reportId) {
        return dto(require(ids.toLong(reportId)));
    }

    @Transactional(readOnly = true)
    public GrowthReportPageResponseAllOfData list(String studentId, ReportType reportType,
            GrowthReportStatus status, LocalDate startDate, LocalDate endDate, Integer page, Integer pageSize) {
        Long studentKey = ids.toLong(studentId);
        requireStudent(studentKey);
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) throw rule("invalid date range");
        int number = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        var query = Wrappers.<GrowthReportEntity>lambdaQuery()
                .eq(GrowthReportEntity::getStudentId, studentKey)
                .eq(reportType != null, GrowthReportEntity::getReportType,
                        reportType == null ? null : reportType.getValue())
                .eq(status != null, GrowthReportEntity::getStatus, status == null ? null : status.getValue())
                .ge(startDate != null, GrowthReportEntity::getStartDate, startDate)
                .le(endDate != null, GrowthReportEntity::getEndDate, endDate)
                .orderByDesc(GrowthReportEntity::getCreateTime).orderByDesc(GrowthReportEntity::getId);
        Page<GrowthReportEntity> result = reports.selectPage(new Page<>(number, size), query);
        return new GrowthReportPageResponseAllOfData(number, size, result.getTotal(),
                Math.toIntExact(result.getPages())).items(result.getRecords().stream().map(this::dto).toList());
    }

    @Transactional
    public GrowthReportDto cancel(String reportId) {
        Long id = ids.toLong(reportId);
        GrowthReportEntity report = reports.selectForUpdate(id);
        if (report == null) throw new ResourceNotFoundException("growth report not found");
        if ("PENDING".equals(report.getStatus())) {
            reports.update(null, Wrappers.<GrowthReportEntity>lambdaUpdate()
                    .eq(GrowthReportEntity::getId, id).eq(GrowthReportEntity::getStatus, "PENDING")
                    .set(GrowthReportEntity::getStatus, "CANCELLED")
                    .set(GrowthReportEntity::getCancelRequested, true)
                    .set(GrowthReportEntity::getFinishTime, time.localDateTime()).setSql("version=version+1"));
        } else if ("RUNNING".equals(report.getStatus())) {
            reports.update(null, Wrappers.<GrowthReportEntity>lambdaUpdate()
                    .eq(GrowthReportEntity::getId, id).eq(GrowthReportEntity::getStatus, "RUNNING")
                    .set(GrowthReportEntity::getCancelRequested, true).setSql("version=version+1"));
        } else {
            throw conflict("growth report cannot be cancelled in its current state");
        }
        return dto(require(id));
    }

    @Transactional
    public GrowthReportDto regenerate(String reportId, GrowthReportRegenerateRequest request) {
        if (request != null && request.getModelId() != null) throw rule("modelId is not supported");
        Long sourceId = ids.toLong(reportId);
        GrowthReportEntity source = reports.selectForUpdate(sourceId);
        if (source == null) throw new ResourceNotFoundException("growth report not found");
        if (!"SUCCESS".equals(source.getStatus())) throw conflict("only successful reports can be regenerated");
        if (reports.countActiveChild(sourceId) != 0) throw conflict("report regeneration is already active");

        GrowthReportEntity report = new GrowthReportEntity();
        report.setStudentId(source.getStudentId());
        report.setReportType(source.getReportType());
        report.setTitle(source.getTitle());
        report.setStartDate(source.getStartDate());
        report.setEndDate(source.getEndDate());
        report.setGenerationType("DETERMINISTIC");
        report.setStatus("PENDING");
        report.setSourceReportId(sourceId);
        report.setRequestJson(write(new CanonicalRequest(1, GENERATION_VERSION, source.getStudentId().toString(),
                source.getReportType(), source.getTitle(), source.getStartDate(), source.getEndDate(),
                request == null ? null : request.getReason())));
        report.setSnapshotSchemaVersion(1);
        report.setGenerationVersion(GENERATION_VERSION);
        report.setProgressPercent(0);
        report.setCancelRequested(false);
        if (reports.insert(report) != 1) throw new IllegalStateException("growth report was not regenerated");
        afterCommit(report.getId());
        return dto(report);
    }

    @Transactional(readOnly = true)
    public Download export(String reportId, String format, Boolean includeAttachments) {
        GrowthReportEntity report = require(ids.toLong(reportId));
        if (!"SUCCESS".equals(report.getStatus())) throw conflict("growth report is not ready for export");
        if (Boolean.TRUE.equals(includeAttachments)) throw rule("growth report attachment export is not supported");
        String normalized = format == null ? "" : format.toUpperCase(java.util.Locale.ROOT);
        byte[] content;
        String extension;
        String mime;
        if ("MARKDOWN".equals(normalized)) {
            content = report.getContentMarkdown().getBytes(StandardCharsets.UTF_8);
            extension = "md";
            mime = "text/markdown;charset=UTF-8";
        } else if ("JSON".equals(normalized)) {
            content = (report.getStatisticsSnapshotJson() + "\n").getBytes(StandardCharsets.UTF_8);
            extension = "json";
            mime = "application/json";
        } else {
            throw rule("unsupported growth report export format");
        }
        return new Download(new ByteArrayResource(content), "growth-report-" + report.getId() + "." + extension,
                mime, content.length);
    }

    private Long validate(GrowthReportCreateRequest request) {
        Long studentId = ids.toLong(request.getStudentId());
        requireStudent(studentId);
        if (request.getReportType() == null || request.getStartDate() == null || request.getEndDate() == null) {
            throw rule("reportType, startDate and endDate are required");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) throw rule("title is required");
        if (request.getGenerationType() != null && request.getGenerationType() != GenerationType.DETERMINISTIC) {
            throw rule("only deterministic generation is supported");
        }
        if (request.getModelId() != null) throw rule("modelId is not supported");
        if (Boolean.TRUE.equals(request.getIncludeAiAnalysis())) throw rule("AI analysis is not supported");
        if (Boolean.TRUE.equals(request.getIncludeAttachments())) throw rule("attachments are not supported");
        validatePeriod(request.getReportType(), request.getStartDate(), request.getEndDate());
        return studentId;
    }

    private void validatePeriod(ReportType type, LocalDate start, LocalDate end) {
        if (end.isBefore(start)) throw rule("endDate must not be before startDate");
        if (end.isAfter(time.today())) throw rule("endDate must not be in the future");
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        long maximum = switch (type) {
            case DAILY -> 1;
            case WEEKLY -> 7;
            case MONTHLY -> 31;
            case TERM, YEARLY, CUSTOM -> 366;
        };
        if (days > maximum) throw rule("report period exceeds the reportType limit");
    }

    private GrowthReportDto dto(GrowthReportEntity report) {
        GrowthReportDto dto = new GrowthReportDto();
        dto.setId(report.getId().toString());
        dto.setStudentId(report.getStudentId().toString());
        dto.setReportType(ReportType.fromValue(report.getReportType()));
        dto.setTitle(report.getTitle());
        dto.setStartDate(report.getStartDate());
        dto.setEndDate(report.getEndDate());
        dto.setGenerationType(GenerationType.DETERMINISTIC);
        dto.setModelId(null);
        dto.setIncludeAiAnalysis(false);
        dto.setIncludeAttachments(false);
        dto.setSourceReportId(report.getSourceReportId() == null ? null : report.getSourceReportId().toString());
        dto.setStatus(GrowthReportStatus.fromValue(report.getStatus()));
        dto.setSnapshotSchemaVersion(report.getSnapshotSchemaVersion());
        dto.setGenerationVersion(report.getGenerationVersion());
        dto.setProgressPercent(report.getProgressPercent());
        dto.setStatisticsSnapshot(readSnapshot(report.getStatisticsSnapshotJson()));
        dto.setAiAnalysisId(null);
        dto.setContentMarkdown(report.getContentMarkdown());
        dto.setErrorCode(report.getErrorCode());
        dto.setErrorMessage(report.getErrorMessage());
        dto.setStartedAt(time.toOffsetDateTime(report.getStartTime()));
        dto.setFinishedAt(time.toOffsetDateTime(report.getFinishTime()));
        dto.setVersion(report.getVersion());
        dto.setCreatedAt(time.toOffsetDateTime(report.getCreateTime()));
        dto.setUpdatedAt(time.toOffsetDateTime(report.getUpdateTime()));
        return dto;
    }

    private GrowthReportSnapshotDto readSnapshot(String value) {
        if (value == null) return null;
        try { return json.readValue(value, GrowthReportSnapshotDto.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("stored growth report snapshot is invalid", ex); }
    }

    private GrowthReportEntity require(Long id) {
        GrowthReportEntity report = reports.selectById(id);
        if (report == null) throw new ResourceNotFoundException("growth report not found");
        return report;
    }

    private void requireStudent(Long id) {
        if (students.selectById(id) == null) throw new ResourceNotFoundException("student not found");
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("growth report request serialization failed", ex); }
    }

    private void afterCommit(Long id) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { dispatcher.dispatch(id); }
        });
    }

    private BusinessException rule(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BusinessException conflict(String message) {
        return new BusinessException("TASK_STATE_CONFLICT", message, HttpStatus.CONFLICT);
    }

    public record Download(ByteArrayResource content, String fileName, String mimeType, long size) { }
    private record CanonicalRequest(int schemaVersion, String generationVersion, String studentId,
            String reportType, String title, LocalDate startDate, LocalDate endDate, String reason) { }
}
