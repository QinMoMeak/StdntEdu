package com.stdntedu.ai.extraction.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ai_extraction_correction")
public class AiExtractionCorrectionEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long taskId;
    private Long questionId;
    private String fieldName;
    private String originalValue;
    private String correctedValue;
    private LocalDateTime createTime;
}
