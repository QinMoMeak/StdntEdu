package com.stdntedu.ai.extraction.service;

import java.util.List;

import com.stdntedu.ai.extraction.mapper.AiExtractionTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AiExtractionRecoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(AiExtractionRecoveryService.class);
    private static final int BATCH_SIZE = 100;

    private final AiExtractionTaskMapper tasks;
    private final AiExtractionPersistenceService persistence;
    private final AiExtractionDispatcher dispatcher;
    private final int rescanBatchSize;

    public AiExtractionRecoveryService(AiExtractionTaskMapper tasks,
            AiExtractionPersistenceService persistence, AiExtractionDispatcher dispatcher,
            @Value("${app.ai.extraction.pending-rescan.batch-size:50}") int rescanBatchSize) {
        this.tasks = tasks;
        this.persistence = persistence;
        this.dispatcher = dispatcher;
        this.rescanBatchSize = Math.max(1, rescanBatchSize);
    }

    public void recover() {
        int reset = recoverRunning();
        int dispatched = dispatchPending();
        LOG.info("AI extraction recovery completed: resetRunning={}, pendingDispatched={}", reset, dispatched);
    }

    private int recoverRunning() {
        int recovered = 0;
        long afterId = 0;
        while (true) {
            List<Long> ids = tasks.selectIdsByStatusAfter("RUNNING", afterId, BATCH_SIZE);
            for (Long id : ids) {
                if (persistence.questionCount(id) == 0) recovered += tasks.resetRunning(id);
                else recovered += tasks.recoverReviewRequired(id);
            }
            if (ids.isEmpty() || ids.size() < BATCH_SIZE) return recovered;
            afterId = ids.get(ids.size() - 1);
        }
    }

    private int dispatchPending() {
        int dispatched = 0;
        long afterId = 0;
        while (true) {
            List<Long> ids = tasks.selectIdsByStatusAfter("PENDING", afterId, BATCH_SIZE);
            for (Long id : ids) {
                if (dispatcher.dispatch(id)) dispatched++;
            }
            if (ids.isEmpty() || ids.size() < BATCH_SIZE) return dispatched;
            afterId = ids.get(ids.size() - 1);
        }
    }

    @Scheduled(fixedDelayString = "${app.ai.extraction.pending-rescan.fixed-delay-ms:30000}",
            initialDelayString = "${app.ai.extraction.pending-rescan.initial-delay-ms:30000}")
    public void rescanPending() {
        try {
            for (Long id : tasks.selectIdsByStatusAfter("PENDING", 0L, rescanBatchSize)) {
                dispatcher.dispatch(id);
            }
        } catch (RuntimeException ex) {
            LOG.warn("AI extraction pending rescan failed; the next cycle will retry");
        }
    }
}
