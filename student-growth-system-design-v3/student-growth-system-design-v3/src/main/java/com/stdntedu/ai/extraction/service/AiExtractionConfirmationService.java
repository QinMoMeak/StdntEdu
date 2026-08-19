package com.stdntedu.ai.extraction.service;

import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.AiConfirm;
import com.stdntedu.generated.model.AiConfirmResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AiExtractionConfirmationService {
    private final CanonicalConfirmationHasher hasher;
    private final AiExtractionConfirmationTransactionalService transactions;
    private final IdConverter ids;

    public AiExtractionConfirmationService(CanonicalConfirmationHasher hasher,
            AiExtractionConfirmationTransactionalService transactions, IdConverter ids) {
        this.hasher = hasher;
        this.transactions = transactions;
        this.ids = ids;
    }

    public AiConfirmResult confirm(String taskId, String idempotencyKey, AiConfirm request) {
        if (idempotencyKey == null || idempotencyKey.length() < 8 || idempotencyKey.length() > 64) {
            throw new BusinessException("BUSINESS_RULE_VIOLATION", "Idempotency-Key must contain 8 to 64 characters",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (request == null || request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw new BusinessException("BUSINESS_RULE_VIOLATION", "confirmation questions are required",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Long id = ids.toLong(taskId);
        String hash = hasher.hash(request);
        try {
            return transactions.confirm(id, idempotencyKey, hash, request);
        } catch (DuplicateKeyException ex) {
            AiConfirmResult replay = transactions.replay(id, idempotencyKey, hash);
            if (replay != null) return replay;
            throw ex;
        }
    }
}
