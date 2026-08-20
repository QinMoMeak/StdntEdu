package com.stdntedu.ai.analysis.converter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.analysis.projection.AiAnalysisRow;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.AiAnalysisDto;
import com.stdntedu.generated.model.AiTaskStatus;
import com.stdntedu.generated.model.StudyPlanDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisConverter {
    private static final String REDACTED_ERROR = "AI analysis failed (details redacted)";
    private static final Set<String> SENSITIVE_MARKERS = Set.of(
            "authorization", "api_key", "apikey", "secret", "password", "bearer ", "storage_path");

    private final IdConverter ids;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public AiAnalysisConverter(IdConverter ids, ObjectMapper objectMapper, Validator validator) {
        this.ids = ids;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public AiAnalysisDto toDto(AiAnalysisRow row, ZoneId zone) {
        return new AiAnalysisDto()
                .id(ids.toString(row.getId()))
                .studentId(ids.toString(row.getStudentId()))
                .businessType(row.getBusinessType())
                .businessId(ids.toString(row.getBusinessId()))
                .modelId(ids.toString(row.getAiModelId()))
                .modelName(row.getModelName())
                .promptTemplateId(ids.toString(row.getPromptTemplateId()))
                .status(row.getStatus())
                .inputSummary(row.getInputSummary())
                .result(result(row))
                .errorCode(row.getErrorCode())
                .errorMessage(safeError(row.getErrorMessage()))
                .promptTokens(row.getPromptTokens())
                .completionTokens(row.getCompletionTokens())
                .durationMs(row.getDurationMs())
                .startedAt(offset(row.getStartedTime(), zone))
                .finishedAt(offset(row.getFinishedTime(), zone))
                .estimatedCost(row.getEstimatedCost())
                .currencyCode(row.getCurrencyCode())
                .createdAt(offset(row.getCreateTime(), zone));
    }

    private StudyPlanDto result(AiAnalysisRow row) {
        if (row.getStatus() != AiTaskStatus.SUCCESS) return null;
        if (row.getResultJson() == null) throw corruptSnapshot();
        try {
            StudyPlanDto snapshot = objectMapper.readValue(row.getResultJson(), StudyPlanDto.class);
            Set<ConstraintViolation<StudyPlanDto>> violations = validator.validate(snapshot);
            if (!violations.isEmpty()) throw corruptSnapshot();
            return snapshot;
        } catch (JsonProcessingException ex) {
            throw corruptSnapshot();
        }
    }

    private String safeError(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        if (SENSITIVE_MARKERS.stream().anyMatch(normalized::contains)
                || normalized.matches(".*[a-z]:\\\\.*") || normalized.contains("/tmp/")) {
            return REDACTED_ERROR;
        }
        return value;
    }

    private java.time.OffsetDateTime offset(LocalDateTime value, ZoneId zone) {
        return value == null ? null : value.atZone(zone).toOffsetDateTime();
    }

    private IllegalStateException corruptSnapshot() {
        return new IllegalStateException("AI analysis result snapshot is invalid");
    }
}
