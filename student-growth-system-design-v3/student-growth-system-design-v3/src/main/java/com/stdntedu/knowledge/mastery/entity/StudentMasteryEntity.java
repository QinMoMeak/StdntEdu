package com.stdntedu.knowledge.mastery.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

@Data
@TableName("student_mastery")
public class StudentMasteryEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long studentId;
    private Long knowledgeId;
    private BigDecimal masteryScore;
    private Integer correctCount;
    private Integer wrongCount;
    private Integer partialCount;
    private Integer reviewCount;
    private Integer continuousCorrectCount;
    private Integer continuousWrongCount;
    private Integer evidenceCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime lastPracticeTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime lastReviewTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime nextReviewTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime decayBaseTime;
    private Boolean manualLocked;
    @Version private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
