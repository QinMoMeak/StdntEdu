package com.stdntedu.backup.service;

import java.nio.file.Path;
import java.util.List;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.extraction.resource.OriginalFileStorage;
import com.stdntedu.backup.entity.BackupRecordEntity;
import com.stdntedu.backup.entity.RestoreRecordEntity;
import com.stdntedu.backup.mapper.BackupRecordMapper;
import com.stdntedu.backup.mapper.RestoreRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BackupRestoreRecoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(BackupRestoreRecoveryService.class);
    private final BackupRecordMapper backups;
    private final RestoreRecordMapper restores;
    private final BackupRestoreDispatcher dispatcher;
    private final OriginalFileStorage storage;
    private final ObjectMapper json;
    private final int batch;

    public BackupRestoreRecoveryService(BackupRecordMapper backups, RestoreRecordMapper restores,
            BackupRestoreDispatcher dispatcher, OriginalFileStorage storage, ObjectMapper json,
            @Value("${app.backup-restore.pending-rescan.batch-size:20}") int batch) {
        this.backups = backups; this.restores = restores; this.dispatcher = dispatcher;
        this.storage = storage; this.json = json; this.batch = Math.max(1, batch);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        recoverInterrupted();
        rescanPending();
    }

    void recoverInterrupted() {
        backups.update(null, Wrappers.<BackupRecordEntity>lambdaUpdate()
                .eq(BackupRecordEntity::getStatus, "RUNNING")
                .set(BackupRecordEntity::getStatus, "PENDING")
                .set(BackupRecordEntity::getStartTime, null));
        List<RestoreRecordEntity> interrupted = restores.selectList(Wrappers.<RestoreRecordEntity>lambdaQuery()
                .eq(RestoreRecordEntity::getStatus, "RUNNING").eq(RestoreRecordEntity::getDatabaseApplied, false));
        for (RestoreRecordEntity task : interrupted) {
            cleanupCheckpoint(task.getCheckpointJson());
            restores.update(null, Wrappers.<RestoreRecordEntity>lambdaUpdate()
                    .eq(RestoreRecordEntity::getId, task.getId()).eq(RestoreRecordEntity::getStatus, "RUNNING")
                    .eq(RestoreRecordEntity::getDatabaseApplied, false)
                    .set(RestoreRecordEntity::getStatus, "PENDING")
                    .set(RestoreRecordEntity::getProgressStage, "QUEUED")
                    .set(RestoreRecordEntity::getProgressPercent, 0)
                    .set(RestoreRecordEntity::getStartTime, null)
                    .set(RestoreRecordEntity::getCheckpointJson, null));
        }
    }

    @Scheduled(fixedDelayString = "${app.backup-restore.pending-rescan.fixed-delay-ms:30000}",
            initialDelayString = "${app.backup-restore.pending-rescan.initial-delay-ms:30000}")
    public void rescanPending() {
        try {
            for (Long id : backups.selectPendingIds(0, batch)) dispatcher.dispatchBackup(id);
            for (Long id : restores.selectPendingIds(0, batch)) dispatcher.dispatchRestore(id);
            for (Long id : restores.selectFinalizingIds(batch)) dispatcher.dispatchRestore(id);
        } catch (RuntimeException ex) { LOG.warn("Backup/restore pending rescan failed; the next cycle will retry"); }
    }

    private void cleanupCheckpoint(String value) {
        if (value == null) return;
        try {
            var checkpoint = json.readValue(value, RestoreTaskWorker.RestoreCheckpoint.class);
            checkpoint.newPaths().values().stream().map(Path::of).forEach(storage::cleanup);
        } catch (Exception ex) { LOG.warn("Interrupted restore staging cleanup could not be completed"); }
    }
}
