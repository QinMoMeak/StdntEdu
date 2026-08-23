package com.stdntedu.backup.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("restore_record")
public class RestoreRecordEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String restoreCode;
    private Long backupId;
    private String status;
    private String progressStage;
    private Integer progressPercent;
    private Long preRestoreBackupId;
    private String optionsJson;
    private String inputManifestJson;
    private String checkpointJson;
    private Boolean cancelRequested;
    private Boolean databaseApplied;
    private Boolean filesFinalized;
    private Integer restoredTableCount;
    private Integer restoredAttachmentCount;
    private Integer warningCount;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime finishTime;
}
