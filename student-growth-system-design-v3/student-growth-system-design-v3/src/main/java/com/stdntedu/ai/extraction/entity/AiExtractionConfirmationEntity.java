package com.stdntedu.ai.extraction.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ai_extraction_confirmation")
public class AiExtractionConfirmationEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long taskId;
    private String idempotencyKey;
    private String requestHash;
    private String status;
    private String resultJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
