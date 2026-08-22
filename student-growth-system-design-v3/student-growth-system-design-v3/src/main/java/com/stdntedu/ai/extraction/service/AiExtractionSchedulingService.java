package com.stdntedu.ai.extraction.service;

import java.util.List;

import com.stdntedu.ai.extraction.entity.AiExtractionTaskEntity;
import com.stdntedu.ai.extraction.resource.PreparedExtraction;
import com.stdntedu.ai.extraction.resource.StoredOriginal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AiExtractionSchedulingService {
    private final AiExtractionPersistenceService persistence;
    private final AiExtractionDispatcher dispatcher;

    public AiExtractionSchedulingService(AiExtractionPersistenceService persistence,
            AiExtractionDispatcher dispatcher) {
        this.persistence = persistence;
        this.dispatcher = dispatcher;
    }

    @Transactional
    public CreatedExtraction create(CreateExtractionCommand command, PreparedExtraction prepared,
            List<StoredOriginal> stored) {
        CreatedExtraction created = persistence.createRecords(command, prepared, stored);
        afterCommit(created.taskId());
        return created;
    }

    @Transactional
    public AiExtractionTaskEntity retry(Long taskId, String expectedStatus, Long modelId, boolean reset) {
        AiExtractionTaskEntity pending = persistence.beginRetry(taskId, expectedStatus, modelId, reset);
        afterCommit(taskId);
        return pending;
    }

    private void afterCommit(Long taskId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatcher.dispatch(taskId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { dispatcher.dispatch(taskId); }
        });
    }
}
