package com.stdntedu.ai.analysis.generation;

import com.stdntedu.ai.analysis.mapper.AiAnalysisMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiStudyPlanGenerationFailureService {
    private final AiAnalysisMapper analyses;

    public AiStudyPlanGenerationFailureService(AiAnalysisMapper analyses) { this.analyses = analyses; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(Long analysisId, String code, String message, Integer promptTokens,
            Integer completionTokens) {
        return analyses.markFailed(analysisId, safeCode(code), safeMessage(message),
                promptTokens, completionTokens) == 1;
    }

    private String safeCode(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{1,64}")) return "GENERATION_FAILED";
        return value;
    }

    private String safeMessage(String value) {
        if (value == null || value.isBlank()) return "study plan generation failed";
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("authorization") || normalized.contains("secret")
                || normalized.contains("api_key") || normalized.contains("apikey")
                || normalized.contains("bearer ") || normalized.contains("password")
                || normalized.matches(".*[a-z]:\\\\.*") || normalized.contains("/tmp/")) {
            return "study plan generation failed (details redacted)";
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
