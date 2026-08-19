package com.stdntedu.ai.extraction.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ai_extraction_question")
public class AiExtractionQuestionEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long taskId;
    private Integer sequenceNo;
    private Integer pageNo;
    private String questionNo;
    private String questionType;
    private String questionText;
    private String studentAnswer;
    private String correctAnswer;
    private String answerSource;
    private String analysisText;
    private String analysisSource;
    private String errorType;
    private Integer difficulty;
    private BigDecimal confidence;
    private String status;
    private String rawJson;
    private Boolean userModified;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
