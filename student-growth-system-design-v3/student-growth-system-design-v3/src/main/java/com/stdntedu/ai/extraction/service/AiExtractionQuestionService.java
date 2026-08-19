package com.stdntedu.ai.extraction.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.ai.extraction.converter.AiExtractionConverter;
import com.stdntedu.ai.extraction.entity.AiExtractionCorrectionEntity;
import com.stdntedu.ai.extraction.entity.AiExtractionQuestionEntity;
import com.stdntedu.ai.extraction.entity.AiExtractionQuestionKnowledgeEntity;
import com.stdntedu.ai.extraction.entity.AiExtractionTaskEntity;
import com.stdntedu.ai.extraction.mapper.AiExtractionCorrectionMapper;
import com.stdntedu.ai.extraction.mapper.AiExtractionQuestionKnowledgeMapper;
import com.stdntedu.ai.extraction.mapper.AiExtractionQuestionMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.AiExtractionQuestionDto;
import com.stdntedu.generated.model.AiExtractionQuestionStatus;
import com.stdntedu.generated.model.AiExtractionQuestionUpdateRequest;
import com.stdntedu.generated.model.InlineObject8AllOfData;
import com.stdntedu.generated.model.KnowledgeLink;
import com.stdntedu.score.entity.KnowledgeNodeReferenceEntity;
import com.stdntedu.score.mapper.KnowledgeNodeReferenceMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiExtractionQuestionService {
    private final AiExtractionPersistenceService persistence;
    private final AiExtractionQuestionMapper questions;
    private final AiExtractionQuestionKnowledgeMapper links;
    private final AiExtractionCorrectionMapper corrections;
    private final KnowledgeNodeReferenceMapper knowledge;
    private final AiExtractionConverter converter;
    private final IdConverter ids;

    public AiExtractionQuestionService(AiExtractionPersistenceService persistence,
            AiExtractionQuestionMapper questions, AiExtractionQuestionKnowledgeMapper links,
            AiExtractionCorrectionMapper corrections, KnowledgeNodeReferenceMapper knowledge,
            AiExtractionConverter converter, IdConverter ids) {
        this.persistence = persistence;
        this.questions = questions;
        this.links = links;
        this.corrections = corrections;
        this.knowledge = knowledge;
        this.converter = converter;
        this.ids = ids;
    }

    @Transactional(readOnly = true)
    public InlineObject8AllOfData list(String taskId) {
        Long id = ids.toLong(taskId);
        List<AiExtractionQuestionEntity> found = persistence.questions(id);
        Map<Long, List<AiExtractionQuestionKnowledgeEntity>> candidates = persistence.candidates(found.stream()
                .map(AiExtractionQuestionEntity::getId).toList());
        return new InlineObject8AllOfData().page(1).pageSize(Math.max(1, found.size())).total((long) found.size())
                .totalPages(found.isEmpty() ? 0 : 1).items(found.stream()
                        .map(question -> converter.question(question,
                                candidates.getOrDefault(question.getId(), List.of())))
                        .toList());
    }

    @Transactional
    public AiExtractionQuestionDto update(String taskId, String questionId,
            AiExtractionQuestionUpdateRequest request) {
        Long taskKey = ids.toLong(taskId);
        Long questionKey = ids.toLong(questionId);
        AiExtractionTaskEntity task = persistence.requireTask(taskKey);
        AiExtractionQuestionEntity current = require(taskKey, questionKey);
        if ("SAVED".equals(current.getStatus())) throw conflict("saved extraction question cannot be modified");
        if (request.getVersion() == null || !request.getVersion().equals(current.getVersion())) throw versionConflict();

        AiExtractionQuestionEntity updated = copy(current);
        updated.setQuestionType(request.getQuestionType());
        if (request.getQuestionText() != null) updated.setQuestionText(request.getQuestionText().trim());
        updated.setStudentAnswer(request.getStudentAnswer());
        updated.setCorrectAnswer(request.getCorrectAnswer());
        updated.setAnalysisText(request.getAnalysisText());
        updated.setErrorType(request.getErrorType());
        updated.setDifficulty(request.getDifficulty());
        if (request.getStatus() != null) updated.setStatus(validateStatus(request.getStatus()));
        validate(updated);

        List<Change> changes = changes(current, updated);
        if (questions.updateWithVersion(updated, request.getVersion()) == 0) throw versionConflict();
        for (Change change : changes) correction(taskKey, questionKey, change);
        if (request.getKnowledgePoints() != null) replaceKnowledge(task, questionKey, request.getKnowledgePoints());
        return get(taskKey, questionKey);
    }

    @Transactional(readOnly = true)
    public AiExtractionQuestionDto get(Long taskId, Long questionId) {
        AiExtractionQuestionEntity question = require(taskId, questionId);
        List<AiExtractionQuestionKnowledgeEntity> candidates = links.selectList(
                Wrappers.<AiExtractionQuestionKnowledgeEntity>lambdaQuery()
                        .eq(AiExtractionQuestionKnowledgeEntity::getExtractionQuestionId, questionId)
                        .orderByDesc(AiExtractionQuestionKnowledgeEntity::getIsPrimary)
                        .orderByAsc(AiExtractionQuestionKnowledgeEntity::getId));
        return converter.question(question, candidates);
    }

    private void replaceKnowledge(AiExtractionTaskEntity task, Long questionId, List<KnowledgeLink> points) {
        if (task.getSubjectId() == null && !points.isEmpty()) throw invalid("task subject is required for knowledge mapping");
        Set<Long> requested = new HashSet<>();
        int primary = 0;
        for (KnowledgeLink point : points) {
            Long id = ids.toLong(point.getKnowledgeId());
            if (!requested.add(id)) throw invalid("duplicate knowledgeId");
            if (Boolean.TRUE.equals(point.getPrimary()) && ++primary > 1) throw invalid("only one primary knowledge point is allowed");
        }
        Map<Long, KnowledgeNodeReferenceEntity> nodes = requested.isEmpty() ? Map.of()
                : knowledge.selectBatchIds(requested).stream()
                        .collect(Collectors.toMap(KnowledgeNodeReferenceEntity::getId, Function.identity()));
        if (nodes.size() != requested.size() || nodes.values().stream().anyMatch(node ->
                !Boolean.TRUE.equals(node.getEnabled()) || !Objects.equals(node.getSubjectId(), task.getSubjectId()))) {
            throw invalid("knowledge node does not belong to task subject");
        }
        links.delete(Wrappers.<AiExtractionQuestionKnowledgeEntity>lambdaQuery()
                .eq(AiExtractionQuestionKnowledgeEntity::getExtractionQuestionId, questionId));
        for (KnowledgeLink point : points) {
            KnowledgeNodeReferenceEntity node = nodes.get(ids.toLong(point.getKnowledgeId()));
            AiExtractionQuestionKnowledgeEntity entity = new AiExtractionQuestionKnowledgeEntity();
            entity.setExtractionQuestionId(questionId);
            entity.setKnowledgeId(node.getId());
            entity.setKnowledgeCode(node.getNodeCode());
            entity.setKnowledgeName(node.getName());
            entity.setConfidence(point.getConfidence());
            entity.setIsPrimary(Boolean.TRUE.equals(point.getPrimary()));
            entity.setConfirmed(true);
            entity.setSource("USER");
            links.insert(entity);
        }
    }

    private AiExtractionQuestionEntity require(Long taskId, Long questionId) {
        AiExtractionQuestionEntity question = questions.selectOne(Wrappers.<AiExtractionQuestionEntity>lambdaQuery()
                .eq(AiExtractionQuestionEntity::getId, questionId)
                .eq(AiExtractionQuestionEntity::getTaskId, taskId));
        if (question == null) throw new ResourceNotFoundException("AI extraction question not found");
        return question;
    }

    private String validateStatus(AiExtractionQuestionStatus status) {
        if (status == AiExtractionQuestionStatus.SAVED) throw invalid("SAVED status is controlled by confirmation");
        return status.getValue();
    }

    private void validate(AiExtractionQuestionEntity question) {
        if (question.getQuestionText() == null || question.getQuestionText().isBlank()) {
            throw invalid("questionText is required");
        }
        if (question.getDifficulty() != null && (question.getDifficulty() < 1 || question.getDifficulty() > 5)) {
            throw invalid("difficulty must be between 1 and 5");
        }
    }

    private void correction(Long taskId, Long questionId, Change change) {
        AiExtractionCorrectionEntity entity = new AiExtractionCorrectionEntity();
        entity.setTaskId(taskId);
        entity.setQuestionId(questionId);
        entity.setFieldName(change.field());
        entity.setOriginalValue(change.before());
        entity.setCorrectedValue(change.after());
        corrections.insert(entity);
    }

    private List<Change> changes(AiExtractionQuestionEntity before, AiExtractionQuestionEntity after) {
        List<Change> result = new ArrayList<>();
        changed(result, "questionType", before.getQuestionType(), after.getQuestionType());
        changed(result, "questionText", before.getQuestionText(), after.getQuestionText());
        changed(result, "studentAnswer", before.getStudentAnswer(), after.getStudentAnswer());
        changed(result, "correctAnswer", before.getCorrectAnswer(), after.getCorrectAnswer());
        changed(result, "analysisText", before.getAnalysisText(), after.getAnalysisText());
        changed(result, "errorType", before.getErrorType(), after.getErrorType());
        changed(result, "difficulty", before.getDifficulty(), after.getDifficulty());
        changed(result, "status", before.getStatus(), after.getStatus());
        return result;
    }

    private void changed(List<Change> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) changes.add(new Change(field, string(before), string(after)));
    }

    private String string(Object value) { return value == null ? null : value.toString(); }

    private AiExtractionQuestionEntity copy(AiExtractionQuestionEntity source) {
        AiExtractionQuestionEntity target = new AiExtractionQuestionEntity();
        org.springframework.beans.BeanUtils.copyProperties(source, target);
        return target;
    }

    private BusinessException invalid(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BusinessException conflict(String message) {
        return new BusinessException("TASK_STATE_CONFLICT", message, HttpStatus.CONFLICT);
    }

    private BusinessException versionConflict() {
        return new BusinessException("DATA_VERSION_CONFLICT", "AI extraction question version conflict",
                HttpStatus.CONFLICT);
    }

    private record Change(String field, String before, String after) { }
}
