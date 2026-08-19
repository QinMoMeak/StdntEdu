package com.stdntedu.ai.extraction.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ai_extraction_confirmation_item")
public class AiExtractionConfirmationItemEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long confirmationId;
    private Long questionId;
    private Long wrongQuestionId;
    private LocalDateTime createTime;
}
