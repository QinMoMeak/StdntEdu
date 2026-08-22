package com.stdntedu.ai.analysis.generation;

import com.stdntedu.ai.analysis.mapper.AiAnalysisMapper;
import com.stdntedu.studyplan.mapper.StudyPlanMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class AiStudyPlanGenerationRecovery {
    private static final Logger LOG = LoggerFactory.getLogger(AiStudyPlanGenerationRecovery.class);
    private static final int STARTUP_BATCH_SIZE = 100;
    private final AiAnalysisMapper analyses;
    private final StudyPlanMapper plans;
    private final AiStudyPlanGenerationDispatcher dispatcher;
    private final int rescanBatchSize;

    public AiStudyPlanGenerationRecovery(AiAnalysisMapper analyses, StudyPlanMapper plans,
            AiStudyPlanGenerationDispatcher dispatcher,
            @Value("${app.ai.study-plan.pending-rescan.batch-size:50}") int rescanBatchSize) {
        this.analyses = analyses;
        this.plans = plans;
        this.dispatcher = dispatcher;
        this.rescanBatchSize = Math.max(1, rescanBatchSize);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() { recover(); }

    public void recover() {
        long afterId = 0;
        while (true) {
            List<Long> ids = analyses.selectIdsByStatusAfter("RUNNING", afterId, STARTUP_BATCH_SIZE);
            for (Long id : ids) {
                if (plans.countBySourceAnalysisId(id) > 0) {
                    analyses.markRecoveryConflict(id);
                } else if (analyses.resetRunning(id) == 1) {
                    dispatcher.dispatch(id);
                }
            }
            if (ids.isEmpty() || ids.size() < STARTUP_BATCH_SIZE) break;
            afterId = ids.get(ids.size() - 1);
        }
        afterId = 0;
        while (true) {
            List<Long> ids = analyses.selectIdsByStatusAfter("PENDING", afterId, STARTUP_BATCH_SIZE);
            for (Long id : ids) dispatchPending(id);
            if (ids.isEmpty() || ids.size() < STARTUP_BATCH_SIZE) break;
            afterId = ids.get(ids.size() - 1);
        }
    }

    @Scheduled(fixedDelayString = "${app.ai.study-plan.pending-rescan.fixed-delay-ms:30000}",
            initialDelayString = "${app.ai.study-plan.pending-rescan.initial-delay-ms:30000}")
    public void rescanPending() {
        try {
            for (Long id : analyses.selectIdsByStatusAfter("PENDING", 0L, rescanBatchSize)) dispatchPending(id);
        } catch (RuntimeException ex) {
            LOG.warn("AI study-plan pending rescan failed; the next cycle will retry");
        }
    }

    private void dispatchPending(Long id) {
        if (plans.countBySourceAnalysisId(id) > 0) analyses.markRecoveryConflict(id);
        else dispatcher.dispatch(id);
    }
}
