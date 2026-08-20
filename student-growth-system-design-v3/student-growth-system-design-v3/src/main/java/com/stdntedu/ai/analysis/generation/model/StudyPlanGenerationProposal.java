package com.stdntedu.ai.analysis.generation.model;

import java.time.LocalDate;
import java.util.List;

public record StudyPlanGenerationProposal(
        String title,
        String planType,
        LocalDate startDate,
        LocalDate endDate,
        Integer dailyAvailableMinutes,
        String description,
        List<StudyPlanTaskProposal> tasks) {
}
