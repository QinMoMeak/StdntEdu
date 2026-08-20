package com.stdntedu.ai.analysis.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.stdntedu.generated.model.AiAnalysisBusinessType;
import com.stdntedu.generated.model.AiTaskStatus;
import lombok.Data;

@Data
public class AiAnalysisRow {
    private Long id;
    private Long studentId;
    private AiAnalysisBusinessType businessType;
    private Long businessId;
    private Long aiModelId;
    private String modelName;
    private Long promptTemplateId;
    private AiTaskStatus status;
    private String inputSummary;
    private String resultJson;
    private String errorCode;
    private String errorMessage;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long durationMs;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private BigDecimal estimatedCost;
    private String currencyCode;
    private LocalDateTime createTime;
}
