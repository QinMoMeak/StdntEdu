package com.stdntedu.ai.analysis.generation.model;

import java.time.LocalDate;
import java.util.List;

public record NormalizedStudyPlanGenerationRequest(
        String studentId,
        LocalDate startDate,
        LocalDate endDate,
        Integer dailyAvailableMinutes,
        List<String> subjectIds,
        List<String> targetKnowledgeIds,
        boolean includeWrongQuestionReview,
        boolean includeResources,
        String modelId) {
}
