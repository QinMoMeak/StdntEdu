package com.stdntedu.transfer.service;

import java.util.List;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.transfer.entity.ExportTaskEntity;
import com.stdntedu.transfer.entity.ImportTaskEntity;
import com.stdntedu.transfer.mapper.ExportTaskMapper;
import com.stdntedu.transfer.mapper.ImportTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TransferRecoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(TransferRecoveryService.class);
    private final ImportTaskMapper imports;
    private final ExportTaskMapper exports;
    private final TransferDispatcher dispatcher;
    private final int batch;

    public TransferRecoveryService(ImportTaskMapper imports, ExportTaskMapper exports,
            TransferDispatcher dispatcher, @Value("${app.transfer.pending-rescan.batch-size:50}") int batch) {
        this.imports = imports;
        this.exports = exports;
        this.dispatcher = dispatcher;
        this.batch = Math.max(1, batch);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        imports.update(null, Wrappers.<ImportTaskEntity>lambdaUpdate().eq(ImportTaskEntity::getStatus, "VALIDATING")
                .set(ImportTaskEntity::getStatus, "UPLOADED"));
        imports.update(null, Wrappers.<ImportTaskEntity>lambdaUpdate().eq(ImportTaskEntity::getStatus, "IMPORTING")
                .set(ImportTaskEntity::getStatus, "CONFIRM_PENDING"));
        exports.update(null, Wrappers.<ExportTaskEntity>lambdaUpdate().eq(ExportTaskEntity::getStatus, "RUNNING")
                .set(ExportTaskEntity::getStatus, "PENDING"));
        rescanPending();
    }

    @Scheduled(fixedDelayString = "${app.transfer.pending-rescan.fixed-delay-ms:30000}",
            initialDelayString = "${app.transfer.pending-rescan.initial-delay-ms:30000}")
    public void rescanPending() {
        try {
            dispatchImports("UPLOADED");
            dispatchImports("CONFIRM_PENDING");
            for (Long id : exports.selectIdsByStatusAfter("PENDING", 0, batch)) dispatcher.dispatchExport(id);
        } catch (RuntimeException ex) {
            LOG.warn("Transfer pending rescan failed; the next cycle will retry");
        }
    }

    private void dispatchImports(String status) {
        for (Long id : imports.selectIdsByStatusAfter(status, 0, batch)) dispatcher.dispatchImport(id);
    }
}
