package com.stdntedu.ai.analysis.generation;

import java.time.LocalDate;

import com.stdntedu.ai.analysis.entity.AiAnalysisEntity;
import com.stdntedu.ai.analysis.generation.model.NormalizedStudyPlanGenerationRequest;
import com.stdntedu.ai.analysis.mapper.AiAnalysisMapper;
import com.stdntedu.ai.analysis.service.AiAnalysisService;
import com.stdntedu.ai.model.entity.AiModelEntity;
import com.stdntedu.ai.model.mapper.AiModelMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.AiAnalysisBusinessType;
import com.stdntedu.generated.model.AiAnalysisDto;
import com.stdntedu.generated.model.AiModelType;
import com.stdntedu.generated.model.AiTaskStatus;
import com.stdntedu.generated.model.StudyPlanGenerateRequest;
import com.stdntedu.student.mapper.StudentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AiStudyPlanGenerationService {
    private final AiAnalysisMapper analyses;
    private final AiAnalysisService analysisQueries;
    private final StudentMapper students;
    private final AiModelMapper models;
    private final StudyPlanGenerationInputCodec inputCodec;
    private final StudyPlanGenerationContextLoader contexts;
    private final AiStudyPlanGenerationDispatcher dispatcher;
    private final IdConverter ids;

    public AiStudyPlanGenerationService(AiAnalysisMapper analyses, AiAnalysisService analysisQueries,
            StudentMapper students, AiModelMapper models, StudyPlanGenerationInputCodec inputCodec,
            StudyPlanGenerationContextLoader contexts, AiStudyPlanGenerationDispatcher dispatcher,
            IdConverter ids) {
        this.analyses = analyses;
        this.analysisQueries = analysisQueries;
        this.students = students;
        this.models = models;
        this.inputCodec = inputCodec;
        this.contexts = contexts;
        this.dispatcher = dispatcher;
        this.ids = ids;
    }

    @Transactional
    public AiAnalysisDto generate(String idempotencyKey, StudyPlanGenerateRequest request) {
        validateIdempotencyKey(idempotencyKey);
        NormalizedStudyPlanGenerationRequest normalized = inputCodec.normalize(request);
        Long studentId = ids.toLong(normalized.studentId());
        Long modelId = ids.toLong(normalized.modelId());
        String requestHash = inputCodec.requestHash(normalized);
        AiAnalysisEntity existing = analyses.selectExistingByIdempotency(studentId, idempotencyKey);
        if (existing != null) return replay(existing, requestHash);

        requireStudent(studentId);
        AiModelEntity model = requireModel(modelId);
        validateDateRange(normalized.startDate(), normalized.endDate());
        contexts.load(normalized);

        AiAnalysisEntity pending = new AiAnalysisEntity();
        pending.setStudentId(studentId);
        pending.setBusinessType(AiAnalysisBusinessType.STUDY_PLAN_GENERATION);
        pending.setAiModelId(modelId);
        pending.setStatus(AiTaskStatus.PENDING);
        pending.setInputSummary("AI study plan for student " + studentId + " from "
                + normalized.startDate() + " to " + normalized.endDate());
        pending.setInputJson(inputCodec.encode(normalized));
        pending.setIdempotencyKey(idempotencyKey);
        pending.setRequestHash(requestHash);
        int inserted = analyses.insertPending(pending);

        AiAnalysisEntity accepted = analyses.selectByIdempotency(studentId, idempotencyKey);
        if (accepted == null) throw new IllegalStateException("accepted AI analysis could not be loaded");
        assertSameRequest(accepted, requestHash);
        if (inserted == 1 && accepted.getStatus() == AiTaskStatus.PENDING) afterCommit(accepted.getId());
        return analysisQueries.toDto(accepted, model.getModelName());
    }

    private AiAnalysisDto replay(AiAnalysisEntity existing, String requestHash) {
        assertSameRequest(existing, requestHash);
        AiModelEntity model = models.selectById(existing.getAiModelId());
        if (model == null) throw new IllegalStateException("accepted AI model could not be loaded");
        return analysisQueries.toDto(existing, model.getModelName());
    }

    private void assertSameRequest(AiAnalysisEntity existing, String requestHash) {
        if (!requestHash.equals(existing.getRequestHash())) {
            throw new BusinessException("IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key was already used for a different request", HttpStatus.CONFLICT);
        }
    }

    private void afterCommit(Long analysisId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatcher.dispatch(analysisId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { dispatcher.dispatch(analysisId); }
        });
    }

    private void requireStudent(Long studentId) {
        if (students.selectById(studentId) == null) throw new ResourceNotFoundException("student not found");
    }

    private AiModelEntity requireModel(Long modelId) {
        AiModelEntity model = models.selectById(modelId);
        if (model == null) throw new ResourceNotFoundException("AI model not found");
        if (!Boolean.TRUE.equals(model.getEnabled())) throw rule("AI model is disabled");
        if (model.getModelType() == AiModelType.EMBEDDING) throw rule("embedding model cannot generate study plans");
        return model;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw rule("startDate must not be after endDate");
        }
    }

    private void validateIdempotencyKey(String value) {
        if (value == null || value.length() < 8 || value.length() > 64) {
            throw rule("Idempotency-Key must contain 8 to 64 characters");
        }
    }

    private BusinessException rule(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
