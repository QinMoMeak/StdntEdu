package com.stdntedu.backup.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("backup_record")
public class BackupRecordEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String backupCode;
    private String backupType;
    private String format;
    private Integer manifestSchemaVersion;
    private String compression;
    private String secretMode;
    private Boolean includeAttachments;
    private String status;
    private String fileName;
    private String storagePath;
    private Long fileSize;
    private String checksum;
    private String systemVersion;
    private String databaseVersion;
    private Integer datasetCount;
    private Long recordCount;
    private Integer attachmentCount;
    private String manifestJson;
    private String remark;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime verifiedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime finishTime;
    @TableLogic private Boolean deleted;
}
