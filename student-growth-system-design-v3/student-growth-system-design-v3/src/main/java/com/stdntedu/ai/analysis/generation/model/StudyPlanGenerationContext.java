package com.stdntedu.ai.analysis.generation.model;

import java.util.List;

public record StudyPlanGenerationContext(
        List<String> subjectIds,
        List<String> knowledgeIds,
        List<String> resourceIds,
        List<String> wrongQuestionIds,
        List<String> examIds) {
}
