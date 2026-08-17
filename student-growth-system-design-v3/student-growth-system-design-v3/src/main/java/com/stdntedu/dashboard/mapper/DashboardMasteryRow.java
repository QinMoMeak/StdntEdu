package com.stdntedu.dashboard.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DashboardMasteryRow {
    private Long knowledgeId;
    private String knowledgeCode;
    private String knowledgeName;
    private Long subjectId;
    private String subjectName;
    private BigDecimal masteryScore;
    private Integer evidenceCount;
    private LocalDateTime nextReviewTime;
}
