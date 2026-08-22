package com.stdntedu.ai.extraction.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class AiExtractionDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(AiExtractionDispatcher.class);

    private final AsyncTaskExecutor executor;
    private final AiExtractionWorker worker;
    private final Set<Long> scheduled = ConcurrentHashMap.newKeySet();

    public AiExtractionDispatcher(@Qualifier("aiExtractionExecutor") AsyncTaskExecutor executor,
            AiExtractionWorker worker) {
        this.executor = executor;
        this.worker = worker;
    }

    public boolean dispatch(Long taskId) {
        if (!scheduled.add(taskId)) return false;
        try {
            executor.execute(() -> {
                try {
                    worker.execute(taskId);
                } catch (RuntimeException ex) {
                    LOG.error("AI extraction worker terminated unexpectedly for task {}", taskId);
                } finally {
                    scheduled.remove(taskId);
                }
            });
            return true;
        } catch (RuntimeException ex) {
            scheduled.remove(taskId);
            LOG.warn("AI extraction dispatch was rejected for task {}", taskId);
            return false;
        }
    }

    public boolean isScheduled(Long taskId) { return scheduled.contains(taskId); }
}
