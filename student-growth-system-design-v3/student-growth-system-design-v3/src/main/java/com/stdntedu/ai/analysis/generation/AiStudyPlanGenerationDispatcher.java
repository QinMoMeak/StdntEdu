package com.stdntedu.ai.analysis.generation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class AiStudyPlanGenerationDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(AiStudyPlanGenerationDispatcher.class);

    private final AsyncTaskExecutor executor;
    private final AiStudyPlanGenerationWorker worker;
    private final Set<Long> scheduled = ConcurrentHashMap.newKeySet();

    public AiStudyPlanGenerationDispatcher(@Qualifier("aiStudyPlanExecutor") AsyncTaskExecutor executor,
            AiStudyPlanGenerationWorker worker) {
        this.executor = executor;
        this.worker = worker;
    }

    public boolean dispatch(Long analysisId) {
        if (!scheduled.add(analysisId)) return false;
        try {
            executor.execute(() -> {
                try {
                    worker.execute(analysisId);
                } catch (Exception ex) {
                    LOG.error("AI study-plan worker terminated unexpectedly for analysis {}", analysisId);
                } finally {
                    scheduled.remove(analysisId);
                }
            });
            return true;
        } catch (RuntimeException ex) {
            scheduled.remove(analysisId);
            LOG.warn("AI study-plan dispatch was rejected for analysis {}", analysisId);
            return false;
        }
    }

    public boolean isScheduled(Long analysisId) { return scheduled.contains(analysisId); }
}
