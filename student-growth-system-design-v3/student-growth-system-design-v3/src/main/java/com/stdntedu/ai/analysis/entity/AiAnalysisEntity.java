package com.stdntedu.ai.analysis.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stdntedu.generated.model.AiAnalysisBusinessType;
import com.stdntedu.generated.model.AiTaskStatus;
import lombok.Data;

@Data
@TableName("ai_analysis")
public class AiAnalysisEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studentId;
    private AiAnalysisBusinessType businessType;
    private Long businessId;
    private Long aiModelId;
    private Long promptTemplateId;
    private AiTaskStatus status;
    private String inputSummary;
    private String inputJson;
    private String idempotencyKey;
    private String requestHash;
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
