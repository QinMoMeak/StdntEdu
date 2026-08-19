package com.stdntedu.ai.extraction.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ai_extraction_task")
public class AiExtractionTaskEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String taskCode;
    private Long studentId;
    private Long subjectId;
    private Long gradeId;
    private String sourceType;
    private String sourceName;
    private Long examId;
    private Long modelId;
    private Long promptTemplateId;
    private String status;
    private String progressStage;
    private Integer progressPercent;
    private String inputType;
    private Boolean recognizeAnalysis;
    private Boolean matchKnowledge;
    private Boolean maskPersonalInfo;
    private Integer retryCount;
    private Integer maxRetryCount;
    private String resultJson;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
