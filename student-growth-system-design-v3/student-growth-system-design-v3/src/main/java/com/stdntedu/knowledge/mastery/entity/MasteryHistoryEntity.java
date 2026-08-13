package com.stdntedu.knowledge.mastery.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mastery_history")
public class MasteryHistoryEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studentId;
    private Long knowledgeId;
    private String eventType;
    private String businessType;
    private Long businessId;
    private BigDecimal scoreBefore;
    private BigDecimal changeValue;
    private BigDecimal scoreAfter;
    private BigDecimal difficultyFactor;
    private BigDecimal confidence;
    private BigDecimal knowledgeWeight;
    private BigDecimal streakAdjustment;
    private String calculationDetailJson;
    private Boolean manualFlag;
    private String remark;
    private LocalDateTime createTime;
}
