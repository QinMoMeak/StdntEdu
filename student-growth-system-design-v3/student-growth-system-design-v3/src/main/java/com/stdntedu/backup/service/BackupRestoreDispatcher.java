package com.stdntedu.backup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class BackupRestoreDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(BackupRestoreDispatcher.class);
    private final ThreadPoolTaskExecutor executor;
    private final BackupTaskWorker backups;
    private final RestoreTaskWorker restores;

    public BackupRestoreDispatcher(@Qualifier("backupRestoreExecutor") ThreadPoolTaskExecutor executor,
            BackupTaskWorker backups, RestoreTaskWorker restores) {
        this.executor = executor; this.backups = backups; this.restores = restores;
    }

    public boolean dispatchBackup(Long id) { return submit(() -> backups.run(id)); }
    public boolean dispatchRestore(Long id) { return submit(() -> restores.run(id)); }

    private boolean submit(Runnable task) {
        try { executor.execute(task); return true; }
        catch (TaskRejectedException ex) {
            LOG.warn("Backup/restore executor rejected a task; periodic rescan will retry");
            return false;
        }
    }
}
