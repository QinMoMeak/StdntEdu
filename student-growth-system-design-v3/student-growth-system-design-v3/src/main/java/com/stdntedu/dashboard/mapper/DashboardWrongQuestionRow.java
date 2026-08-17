package com.stdntedu.dashboard.mapper;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DashboardWrongQuestionRow {
    private Long id;
    private Long subjectId;
    private String subjectName;
    private String sourceType;
    private String questionType;
    private String questionText;
    private String status;
    private Integer reviewStage;
    private LocalDateTime nextReviewTime;
}
