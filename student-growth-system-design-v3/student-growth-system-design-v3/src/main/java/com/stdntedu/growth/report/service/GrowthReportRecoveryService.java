package com.stdntedu.growth.report.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.growth.report.entity.GrowthReportEntity;
import com.stdntedu.growth.report.mapper.GrowthReportMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class GrowthReportRecoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(GrowthReportRecoveryService.class);
    private final GrowthReportMapper reports;
    private final GrowthReportDispatcher dispatcher;
    private final int batch;

    public GrowthReportRecoveryService(GrowthReportMapper reports, GrowthReportDispatcher dispatcher,
            @Value("${app.growth-report.pending-rescan.batch-size:50}") int batch) {
        this.reports = reports;
        this.dispatcher = dispatcher;
        this.batch = Math.max(1, batch);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reports.update(null, Wrappers.<GrowthReportEntity>lambdaUpdate()
                .eq(GrowthReportEntity::getStatus, "RUNNING")
                .set(GrowthReportEntity::getStatus, "PENDING")
                .set(GrowthReportEntity::getProgressPercent, 0)
                .set(GrowthReportEntity::getCancelRequested, false)
                .set(GrowthReportEntity::getStartTime, null)
                .set(GrowthReportEntity::getFinishTime, null)
                .set(GrowthReportEntity::getErrorCode, null)
                .set(GrowthReportEntity::getErrorMessage, null).setSql("version=version+1"));
        rescanPending();
    }

    @Scheduled(fixedDelayString = "${app.growth-report.pending-rescan.fixed-delay-ms:30000}",
            initialDelayString = "${app.growth-report.pending-rescan.initial-delay-ms:30000}")
    public void rescanPending() {
        try {
            for (Long id : reports.selectPendingIds(0, batch)) dispatcher.dispatch(id);
        } catch (RuntimeException ex) {
            LOG.warn("Growth report pending rescan failed; the next cycle will retry");
        }
    }
}
