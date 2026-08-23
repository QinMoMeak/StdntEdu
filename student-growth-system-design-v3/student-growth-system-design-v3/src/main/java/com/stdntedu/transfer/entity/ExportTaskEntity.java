package com.stdntedu.transfer.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("export_task")
public class ExportTaskEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String taskCode;
    private Long studentId;
    private String exportTypesJson;
    private String exportFormat;
    private String status;
    private String filterJson;
    private Boolean includeAttachments;
    private Boolean includeDeleted;
    private Integer progressPercent;
    private Long outputAttachmentId;
    private String errorMessage;
    private String errorCode;
    private LocalDateTime expireTime;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
