package com.stdntedu.ai.extraction.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ai_extraction_file")
public class AiExtractionFileEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long taskId;
    private Long attachmentId;
    private Integer pageNo;
    private Integer sortOrder;
    private String fileRole;
    private Integer imageWidth;
    private Integer imageHeight;
    private String preprocessStatus;
    private String ocrStatus;
    private String ocrText;
    private LocalDateTime createTime;
}
