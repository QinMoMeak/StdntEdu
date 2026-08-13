package com.stdntedu.score.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class ScoreListRow {
    private Long id;
    private Long examId;
    private String examName;
    private String examType;
    private LocalDate examDate;
    private Long academicTermId;
    private Long subjectId;
    private String subjectName;
    private BigDecimal score;
    private BigDecimal fullScore;
    private Integer classRank;
    private Integer gradeRank;
}
