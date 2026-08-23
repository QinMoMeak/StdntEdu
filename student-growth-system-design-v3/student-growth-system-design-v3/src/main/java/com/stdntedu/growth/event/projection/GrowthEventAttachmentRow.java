package com.stdntedu.growth.event.projection;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class GrowthEventAttachmentRow {
    private Long eventId;
    private Long attachmentId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String sha256;
    private LocalDateTime createTime;
}
