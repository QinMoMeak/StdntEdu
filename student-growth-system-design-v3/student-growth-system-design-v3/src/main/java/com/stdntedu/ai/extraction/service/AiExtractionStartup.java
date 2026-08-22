package com.stdntedu.ai.extraction.service;

import com.stdntedu.ai.extraction.resource.AttachmentStorageReconciliationService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class AiExtractionStartup {
    private final AttachmentStorageReconciliationService reconciliation;
    private final AiExtractionRecoveryService recovery;

    public AiExtractionStartup(AttachmentStorageReconciliationService reconciliation,
            AiExtractionRecoveryService recovery) {
        this.reconciliation = reconciliation;
        this.recovery = recovery;
    }

    @Order(20)
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        reconciliation.reconcile();
        recovery.recover();
    }
}
