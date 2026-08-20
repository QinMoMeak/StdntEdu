package com.stdntedu.ai.analysis.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.stdntedu.ai.analysis.entity.AiAnalysisEntity;
import com.stdntedu.ai.analysis.converter.AiAnalysisConverter;
import com.stdntedu.ai.analysis.mapper.AiAnalysisQueryMapper;
import com.stdntedu.ai.analysis.projection.AiAnalysisRow;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.AiAnalysisBusinessType;
import com.stdntedu.generated.model.AiAnalysisDto;
import com.stdntedu.generated.model.AiAnalysisPageResponseAllOfData;
import com.stdntedu.generated.model.AiTaskStatus;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAnalysisService {
    private final AiAnalysisQueryMapper queries;
    private final AiAnalysisConverter converter;
    private final SystemTimezoneProvider timezone;
    private final IdConverter ids;

    public AiAnalysisService(AiAnalysisQueryMapper queries, AiAnalysisConverter converter,
            SystemTimezoneProvider timezone, IdConverter ids) {
        this.queries = queries;
        this.converter = converter;
        this.timezone = timezone;
        this.ids = ids;
    }

    @Transactional(readOnly = true)
    public AiAnalysisPageResponseAllOfData list(String studentId, AiAnalysisBusinessType businessType,
            String businessId, String modelId, AiTaskStatus status, OffsetDateTime startTime,
            OffsetDateTime endTime, int page, int pageSize) {
        validateBusinessFilter(businessType, businessId);
        validateTimeRange(startTime, endTime);
        ZoneId zone = timezone.get();
        Long studentKey = optionalId(studentId);
        Long businessKey = optionalId(businessId);
        Long modelKey = optionalId(modelId);
        LocalDateTime start = local(startTime, zone);
        LocalDateTime end = local(endTime, zone);
        long total = queries.count(studentKey, businessType, businessKey, modelKey, status, start, end);
        List<AiAnalysisDto> items = queries.selectPage(studentKey, businessType, businessKey, modelKey, status,
                start, end, (long) (page - 1) * pageSize, pageSize).stream()
                .map(row -> converter.toDto(row, zone)).toList();
        return new AiAnalysisPageResponseAllOfData().page(page).pageSize(pageSize).total(total)
                .totalPages(totalPages(total, pageSize)).items(items);
    }

    @Transactional(readOnly = true)
    public AiAnalysisDto get(String analysisId) {
        AiAnalysisRow row = queries.selectOne(ids.toLong(analysisId));
        if (row == null) throw new ResourceNotFoundException("AI analysis not found");
        return converter.toDto(row, timezone.get());
    }

    public AiAnalysisDto toDto(AiAnalysisEntity entity, String modelName) {
        AiAnalysisRow row = new AiAnalysisRow();
        row.setId(entity.getId());
        row.setStudentId(entity.getStudentId());
        row.setBusinessType(entity.getBusinessType());
        row.setBusinessId(entity.getBusinessId());
        row.setAiModelId(entity.getAiModelId());
        row.setModelName(modelName);
        row.setPromptTemplateId(entity.getPromptTemplateId());
        row.setStatus(entity.getStatus());
        row.setInputSummary(entity.getInputSummary());
        row.setResultJson(entity.getResultJson());
        row.setErrorCode(entity.getErrorCode());
        row.setErrorMessage(entity.getErrorMessage());
        row.setPromptTokens(entity.getPromptTokens());
        row.setCompletionTokens(entity.getCompletionTokens());
        row.setDurationMs(entity.getDurationMs());
        row.setStartedTime(entity.getStartedTime());
        row.setFinishedTime(entity.getFinishedTime());
        row.setEstimatedCost(entity.getEstimatedCost());
        row.setCurrencyCode(entity.getCurrencyCode());
        row.setCreateTime(entity.getCreateTime());
        return converter.toDto(row, timezone.get());
    }

    private void validateBusinessFilter(AiAnalysisBusinessType businessType, String businessId) {
        if (businessId != null && businessType == null) {
            throw rule("businessType is required when businessId is provided");
        }
        if (businessId != null && businessType == AiAnalysisBusinessType.STUDY_PLAN_GENERATION) {
            throw rule("businessId is not supported for study plan generation analyses");
        }
    }

    private void validateTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime != null && endTime != null && !startTime.isBefore(endTime)) {
            throw rule("startTime must be earlier than endTime");
        }
    }

    private Long optionalId(String value) {
        return value == null ? null : ids.toLong(value);
    }

    private LocalDateTime local(OffsetDateTime value, ZoneId zone) {
        return value == null ? null : value.atZoneSameInstant(zone).toLocalDateTime();
    }

    private int totalPages(long total, int pageSize) {
        return (int) ((total + pageSize - 1) / pageSize);
    }

    private BusinessException rule(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
