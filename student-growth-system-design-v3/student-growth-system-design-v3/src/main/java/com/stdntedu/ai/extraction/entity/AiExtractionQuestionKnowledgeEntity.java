package com.stdntedu.ai.extraction.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ai_extraction_question_knowledge")
public class AiExtractionQuestionKnowledgeEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long extractionQuestionId;
    private Long knowledgeId;
    private String knowledgeCode;
    private String knowledgeName;
    private BigDecimal confidence;
    private Boolean isPrimary;
    private Boolean confirmed;
    private String source;
    private LocalDateTime createTime;
}
