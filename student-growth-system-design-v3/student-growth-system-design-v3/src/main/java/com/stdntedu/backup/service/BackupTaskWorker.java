package com.stdntedu.backup.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.backup.entity.BackupRecordEntity;
import com.stdntedu.backup.mapper.BackupRecordMapper;
import com.stdntedu.backup.packageformat.BackupArchiveService;
import com.stdntedu.backup.packageformat.BackupArchiveService.Artifact;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BackupTaskWorker {
    private static final Logger LOG = LoggerFactory.getLogger(BackupTaskWorker.class);
    private final BackupRecordMapper records;
    private final BackupArchiveService archives;
    private final BackupRestoreLock lock;
    private final SystemTimezoneProvider time;

    public BackupTaskWorker(BackupRecordMapper records, BackupArchiveService archives,
            BackupRestoreLock lock, SystemTimezoneProvider time) {
        this.records = records;
        this.archives = archives;
        this.lock = lock;
        this.time = time;
    }

    public void run(Long id) { lock.run(() -> runLocked(id)); }

    private void runLocked(Long id) {
        if (records.update(null, Wrappers.<BackupRecordEntity>lambdaUpdate()
                .eq(BackupRecordEntity::getId, id).eq(BackupRecordEntity::getStatus, "PENDING")
                .eq(BackupRecordEntity::getDeleted, false)
                .set(BackupRecordEntity::getStatus, "RUNNING")
                .set(BackupRecordEntity::getStartTime, time.localDateTime())) != 1) return;
        Artifact artifact = null;
        try {
            BackupRecordEntity record = records.selectById(id);
            artifact = archives.create(id, Boolean.TRUE.equals(record.getIncludeAttachments()), record.getSecretMode());
            int changed = records.update(null, Wrappers.<BackupRecordEntity>lambdaUpdate()
                    .eq(BackupRecordEntity::getId, id).eq(BackupRecordEntity::getStatus, "RUNNING")
                    .set(BackupRecordEntity::getStatus, "SUCCESS")
                    .set(BackupRecordEntity::getFileName, artifact.fileName())
                    .set(BackupRecordEntity::getStoragePath, artifact.path().toString())
                    .set(BackupRecordEntity::getFileSize, artifact.size())
                    .set(BackupRecordEntity::getChecksum, artifact.sha256())
                    .set(BackupRecordEntity::getDatabaseVersion, artifact.manifest().databaseVersion())
                    .set(BackupRecordEntity::getDatasetCount, artifact.manifest().datasetCount())
                    .set(BackupRecordEntity::getRecordCount, artifact.manifest().recordCount())
                    .set(BackupRecordEntity::getAttachmentCount, artifact.manifest().attachmentCount())
                    .set(BackupRecordEntity::getManifestJson, artifact.manifestJson())
                    .set(BackupRecordEntity::getFinishTime, time.localDateTime()));
            if (changed != 1) archives.cleanup(artifact.path());
        } catch (Exception ex) {
            if (artifact != null) archives.cleanup(artifact.path());
            records.update(null, Wrappers.<BackupRecordEntity>lambdaUpdate()
                    .eq(BackupRecordEntity::getId, id).eq(BackupRecordEntity::getStatus, "RUNNING")
                    .set(BackupRecordEntity::getStatus, "FAILED")
                    .set(BackupRecordEntity::getErrorCode, "BACKUP_GENERATION_FAILED")
                    .set(BackupRecordEntity::getErrorMessage, "backup package generation failed")
                    .set(BackupRecordEntity::getFinishTime, time.localDateTime()));
            LOG.warn("Backup generation failed for task {} ({})", id, ex.getClass().getSimpleName());
        }
    }
}
