package com.stdntedu.ai.analysis.generation.model;

public record StudyPlanGenerationInput(
        int schemaVersion,
        String promptVersion,
        NormalizedStudyPlanGenerationRequest request) {
}
