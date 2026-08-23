package com.stdntedu.growth.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class GrowthReportDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(GrowthReportDispatcher.class);
    private final ThreadPoolTaskExecutor executor;
    private final GrowthReportWorker worker;

    public GrowthReportDispatcher(@Qualifier("growthReportExecutor") ThreadPoolTaskExecutor executor,
            GrowthReportWorker worker) {
        this.executor = executor;
        this.worker = worker;
    }

    public boolean dispatch(Long id) {
        try {
            executor.execute(() -> worker.run(id));
            return true;
        } catch (TaskRejectedException ex) {
            LOG.warn("Growth report executor rejected a task; periodic rescan will retry");
            return false;
        }
    }
}
