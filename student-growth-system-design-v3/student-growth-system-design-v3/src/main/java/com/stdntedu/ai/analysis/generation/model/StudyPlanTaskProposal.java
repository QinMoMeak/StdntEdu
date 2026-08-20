package com.stdntedu.ai.analysis.generation.model;

import java.time.LocalDate;

import com.stdntedu.generated.model.StudyPlanTaskType;

public record StudyPlanTaskProposal(
        LocalDate taskDate,
        StudyPlanTaskType taskType,
        String title,
        String resourceId,
        String wrongQuestionId,
        String knowledgeId,
        String examId,
        Integer expectedDurationSeconds,
        Integer sortOrder,
        String remark) {
}
