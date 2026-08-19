package com.stdntedu.ai.extraction.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.extraction.entity.AiExtractionConfirmationEntity;
import com.stdntedu.ai.extraction.entity.AiExtractionConfirmationItemEntity;
import com.stdntedu.ai.extraction.entity.AiExtractionQuestionEntity;
import com.stdntedu.ai.extraction.entity.AiExtractionTaskEntity;
import com.stdntedu.ai.extraction.mapper.AiExtractionConfirmationItemMapper;
import com.stdntedu.ai.extraction.mapper.AiExtractionConfirmationMapper;
import com.stdntedu.ai.extraction.mapper.AiExtractionQuestionMapper;
import com.stdntedu.ai.extraction.mapper.AiExtractionTaskMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.AiConfirm;
import com.stdntedu.generated.model.AiConfirmItem;
import com.stdntedu.generated.model.AiConfirmResult;
import com.stdntedu.generated.model.AiTaskStatus;
import com.stdntedu.generated.model.WrongCreate;
import com.stdntedu.generated.model.WrongSource;
import com.stdntedu.wrongquestion.service.WrongQuestionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiExtractionConfirmationTransactionalService {
    private final AiExtractionPersistenceService persistence;
    private final AiExtractionConfirmationMapper confirmations;
    private final AiExtractionConfirmationItemMapper confirmationItems;
    private final AiExtractionQuestionMapper questions;
    private final AiExtractionTaskMapper tasks;
    private final WrongQuestionService wrongQuestions;
    private final ObjectMapper objectMapper;
    private final IdConverter ids;

    public AiExtractionConfirmationTransactionalService(AiExtractionPersistenceService persistence,
            AiExtractionConfirmationMapper confirmations,
            AiExtractionConfirmationItemMapper confirmationItems, AiExtractionQuestionMapper questions,
            AiExtractionTaskMapper tasks, WrongQuestionService wrongQuestions, ObjectMapper objectMapper,
            IdConverter ids) {
        this.persistence = persistence;
        this.confirmations = confirmations;
        this.confirmationItems = confirmationItems;
        this.questions = questions;
        this.tasks = tasks;
        this.wrongQuestions = wrongQuestions;
        this.objectMapper = objectMapper;
        this.ids = ids;
    }

    @Transactional
    public AiConfirmResult confirm(Long taskId, String idempotencyKey, String requestHash, AiConfirm request) {
        AiExtractionConfirmationEntity existing = find(taskId, idempotencyKey);
        if (existing != null) return replay(existing, requestHash);
        if (!Boolean.TRUE.equals(request.getAtomic())) throw invalid("atomic must be true");

        AiExtractionTaskEntity task = persistence.requireTask(taskId);
        if (!AiTaskStatus.REVIEW_REQUIRED.getValue().equals(task.getStatus())) {
            throw conflict("task is not awaiting review");
        }
        Map<Long, AiExtractionQuestionEntity> found = loadAndValidateQuestions(taskId, request.getQuestions());
        boolean hasSaved = request.getQuestions().stream().anyMatch(item -> Boolean.TRUE.equals(item.getSave()));
        if (hasSaved && task.getSubjectId() == null) throw invalid("task subject is required before confirmation");

        AiExtractionConfirmationEntity confirmation = new AiExtractionConfirmationEntity();
        confirmation.setTaskId(taskId);
        confirmation.setIdempotencyKey(idempotencyKey);
        confirmation.setRequestHash(requestHash);
        confirmation.setStatus("PROCESSING");
        confirmations.insert(confirmation);

        int created = 0;
        int ignored = 0;
        List<String> wrongIds = new java.util.ArrayList<>();
        for (AiConfirmItem item : request.getQuestions()) {
            Long questionId = ids.toLong(item.getTemporaryQuestionId());
            if (!Boolean.TRUE.equals(item.getSave())) {
                ignored++;
                if (questions.updateStatus(questionId, taskId, "IGNORED") == 0) throw conflict("question state conflict");
                continue;
            }
            Long wrongId = wrongQuestions.createFromAiExtraction(toWrongCreate(task, item), item.getQuestionType(),
                    task.getExamId(), task.getSourceName(), item.getOccurredDate());
            AiExtractionConfirmationItemEntity mapping = new AiExtractionConfirmationItemEntity();
            mapping.setConfirmationId(confirmation.getId());
            mapping.setQuestionId(questionId);
            mapping.setWrongQuestionId(wrongId);
            confirmationItems.insert(mapping);
            if (questions.updateStatus(questionId, taskId, "SAVED") == 0) throw conflict("question state conflict");
            wrongIds.add(wrongId.toString());
            created++;
        }
        if (tasks.markConfirmed(taskId) == 0) throw conflict("task confirmation state conflict");
        AiConfirmResult result = new AiConfirmResult(created, ignored, wrongIds, AiTaskStatus.SUCCESS);
        confirmation.setStatus("COMPLETED");
        confirmation.setResultJson(json(result));
        confirmations.updateById(confirmation);
        return result;
    }

    @Transactional(readOnly = true)
    public AiConfirmResult replay(Long taskId, String idempotencyKey, String requestHash) {
        AiExtractionConfirmationEntity existing = find(taskId, idempotencyKey);
        if (existing == null) return null;
        return replay(existing, requestHash);
    }

    private Map<Long, AiExtractionQuestionEntity> loadAndValidateQuestions(Long taskId, List<AiConfirmItem> items) {
        Set<Long> requested = new HashSet<>();
        for (AiConfirmItem item : items) {
            Long id = ids.toLong(item.getTemporaryQuestionId());
            if (!requested.add(id)) throw invalid("duplicate temporaryQuestionId");
            if (Boolean.TRUE.equals(item.getSave()) && (item.getQuestionText() == null || item.getQuestionText().isBlank())) {
                throw invalid("questionText is required for saved questions");
            }
        }
        Map<Long, AiExtractionQuestionEntity> found = questions.selectList(
                Wrappers.<AiExtractionQuestionEntity>lambdaQuery().eq(AiExtractionQuestionEntity::getTaskId, taskId)
                        .in(AiExtractionQuestionEntity::getId, requested)).stream()
                .collect(Collectors.toMap(AiExtractionQuestionEntity::getId, Function.identity()));
        if (found.size() != requested.size()) throw invalid("temporary question does not belong to task");
        if (found.values().stream().anyMatch(question -> "SAVED".equals(question.getStatus()))) {
            throw conflict("temporary question was already saved");
        }
        return found;
    }

    private WrongCreate toWrongCreate(AiExtractionTaskEntity task, AiConfirmItem item) {
        return new WrongCreate(task.getStudentId().toString(), task.getSubjectId().toString(),
                WrongSource.fromValue(task.getSourceType()), item.getQuestionText().trim())
                .studentAnswer(item.getStudentAnswer())
                .correctAnswer(item.getCorrectAnswer()).analysisText(item.getAnalysisText())
                .errorType(item.getErrorType()).difficulty(item.getDifficulty())
                .knowledgePoints(item.getKnowledgePoints());
    }

    private AiExtractionConfirmationEntity find(Long taskId, String key) {
        return confirmations.selectOne(Wrappers.<AiExtractionConfirmationEntity>lambdaQuery()
                .eq(AiExtractionConfirmationEntity::getTaskId, taskId)
                .eq(AiExtractionConfirmationEntity::getIdempotencyKey, key));
    }

    private AiConfirmResult replay(AiExtractionConfirmationEntity existing, String requestHash) {
        if (!existing.getRequestHash().equalsIgnoreCase(requestHash)) {
            throw new BusinessException("IDEMPOTENCY_CONFLICT", "Idempotency-Key was used for another payload",
                    HttpStatus.CONFLICT);
        }
        if (!"COMPLETED".equals(existing.getStatus()) || existing.getResultJson() == null) {
            throw conflict("confirmation is still processing");
        }
        try { return objectMapper.readValue(existing.getResultJson(), AiConfirmResult.class); }
        catch (JsonProcessingException ex) {
            throw new BusinessException("DATA_INTEGRITY_ERROR", "confirmation result is invalid",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String json(AiConfirmResult result) {
        try { return objectMapper.writeValueAsString(result); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("confirmation result could not be serialized", ex); }
    }

    private BusinessException invalid(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BusinessException conflict(String message) {
        return new BusinessException("TASK_STATE_CONFLICT", message, HttpStatus.CONFLICT);
    }
}
