package com.stdntedu.transfer.service;

import com.stdntedu.transfer.exporttask.ExportTaskWorker;
import com.stdntedu.transfer.importtask.ImportTaskWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class TransferDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(TransferDispatcher.class);
    private final ThreadPoolTaskExecutor executor;
    private final ImportTaskWorker imports;
    private final ExportTaskWorker exports;

    public TransferDispatcher(@Qualifier("transferExecutor") ThreadPoolTaskExecutor executor,
            ImportTaskWorker imports, ExportTaskWorker exports) {
        this.executor = executor;
        this.imports = imports;
        this.exports = exports;
    }

    public boolean dispatchImport(Long id) { return submit(() -> imports.run(id)); }
    public boolean dispatchExport(Long id) { return submit(() -> exports.run(id)); }

    private boolean submit(Runnable task) {
        try {
            executor.execute(task);
            return true;
        } catch (TaskRejectedException ex) {
            LOG.warn("Transfer executor rejected a task; periodic rescan will retry");
            return false;
        }
    }
}
