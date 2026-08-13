package com.stdntedu.knowledge.mastery.evidence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MasteryEvidenceRow {
    private Long businessId;
    private LocalDateTime eventTime;
    private BigDecimal score;
    private BigDecimal fullScore;
    private Integer correctCount;
    private Integer questionCount;
    private String result;
}
