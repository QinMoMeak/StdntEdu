package com.stdntedu.score.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("score_knowledge")
public class ScoreKnowledgeEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long scoreRecordId;
    private Long knowledgeId;
    private BigDecimal score;
    private BigDecimal fullScore;
    private Integer questionCount;
    private Integer correctCount;
    private LocalDateTime createTime;
}
