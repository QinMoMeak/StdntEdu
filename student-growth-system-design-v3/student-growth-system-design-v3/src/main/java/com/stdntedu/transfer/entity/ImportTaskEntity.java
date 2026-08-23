package com.stdntedu.transfer.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("import_task")
public class ImportTaskEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String taskCode;
    private String importType;
    private Long studentId;
    private String status;
    private Long attachmentId;
    private Integer totalRows;
    private Integer validRows;
    private Integer invalidRows;
    private Integer inputFileCount;
    private Integer warningRows;
    private Integer importedRows;
    private Integer skippedRows;
    private Integer failedRows;
    private Integer progressPercent;
    private Integer retryCount;
    private String previewJson;
    private Long errorReportAttachmentId;
    private String errorCode;
    private String errorMessage;
    private String idempotencyKey;
    private String optionsJson;
    private String confirmRequestJson;
    private String confirmRequestHash;
    private LocalDateTime expireTime;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
