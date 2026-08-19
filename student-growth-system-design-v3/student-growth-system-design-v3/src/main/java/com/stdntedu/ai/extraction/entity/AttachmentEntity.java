package com.stdntedu.ai.extraction.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("attachment")
public class AttachmentEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String fileName;
    private String storageType;
    private String storagePath;
    private String mimeType;
    private Long fileSize;
    private String sha256;
    @TableLogic private Boolean deleted;
    private LocalDateTime createTime;
}
