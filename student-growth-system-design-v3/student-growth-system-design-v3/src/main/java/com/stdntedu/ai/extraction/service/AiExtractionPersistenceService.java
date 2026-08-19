package com.stdntedu.ai.extraction.service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.ai.extraction.entity.AiExtractionFileEntity;
import com.stdntedu.ai.extraction.entity.AiExtractionQuestionEntity;
import com.stdntedu.ai.extraction.entity.AiExtractionQuestionKnowledgeEntity;
import com.stdntedu.ai.extraction.entity.AiExtractionTaskEntity;
import com.stdntedu.ai.extraction.entity.AttachmentEntity;
import com.stdntedu.ai.extraction.mapper.AiExtractionCorrectionMapper;
import com.stdntedu.ai.extraction.mapper.AiExtractionFileMapper;
import com.stdntedu.ai.extraction.mapper.AiExtractionQuestionKnowledgeMapper;
import com.stdntedu.ai.extraction.mapper.AiExtractionQuestionMapper;
import com.stdntedu.ai.extraction.mapper.AiExtractionTaskMapper;
import com.stdntedu.ai.extraction.mapper.AttachmentMapper;
import com.stdntedu.ai.extraction.provider.AiExtractedQuestion;
import com.stdntedu.ai.extraction.provider.AiExtractionProviderResult;
import com.stdntedu.ai.extraction.provider.AiKnowledgeCandidate;
import com.stdntedu.ai.extraction.resource.PreparedExtraction;
import com.stdntedu.ai.extraction.resource.StoredOriginal;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.AiTaskStatus;
import com.stdntedu.score.entity.KnowledgeNodeReferenceEntity;
import com.stdntedu.score.mapper.KnowledgeNodeReferenceMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiExtractionPersistenceService {
    private final AttachmentMapper attachments;
    private final AiExtractionTaskMapper tasks;
    private final AiExtractionFileMapper files;
    private final AiExtractionQuestionMapper questions;
    private final AiExtractionQuestionKnowledgeMapper knowledgeLinks;
    private final AiExtractionCorrectionMapper corrections;
    private final KnowledgeNodeReferenceMapper knowledgeNodes;
    private final IdConverter ids;

    public AiExtractionPersistenceService(AttachmentMapper attachments, AiExtractionTaskMapper tasks,
            AiExtractionFileMapper files, AiExtractionQuestionMapper questions,
            AiExtractionQuestionKnowledgeMapper knowledgeLinks, AiExtractionCorrectionMapper corrections,
            KnowledgeNodeReferenceMapper knowledgeNodes, IdConverter ids) {
        this.attachments = attachments;
        this.tasks = tasks;
        this.files = files;
        this.questions = questions;
        this.knowledgeLinks = knowledgeLinks;
        this.corrections = corrections;
        this.knowledgeNodes = knowledgeNodes;
        this.ids = ids;
    }

    @Transactional
    public CreatedExtraction createRecords(CreateExtractionCommand command, PreparedExtraction prepared,
            List<StoredOriginal> stored) {
        AiExtractionTaskEntity task = new AiExtractionTaskEntity();
        task.setTaskCode("AIX-" + UUID.randomUUID().toString().replace("-", ""));
        task.setStudentId(ids.toLong(command.studentId()));
        task.setSubjectId(optionalId(command.subjectId()));
        task.setGradeId(optionalId(command.gradeId()));
        task.setSourceType(command.sourceType().getValue());
        task.setSourceName(clean(command.sourceName(), 255));
        task.setExamId(optionalId(command.examId()));
        task.setModelId(ids.toLong(command.modelId()));
        task.setStatus(AiTaskStatus.PENDING.getValue());
        task.setProgressStage("UPLOADED");
        task.setProgressPercent(0);
        task.setInputType(prepared.inputType().getValue());
        task.setRecognizeAnalysis(command.recognizeAnalysis());
        task.setMatchKnowledge(command.matchKnowledge());
        task.setMaskPersonalInfo(command.maskPersonalInfo());
        task.setRetryCount(0);
        task.setMaxRetryCount(3);
        task.setExpireTime(LocalDateTime.now().plusDays(7));
        tasks.insert(task);

        for (StoredOriginal storedFile : stored) {
            var source = storedFile.source();
            AttachmentEntity attachment = new AttachmentEntity();
            attachment.setFileName(source.originalName());
            attachment.setStorageType("LOCAL");
            attachment.setStoragePath(storedFile.storagePath().toString());
            attachment.setMimeType(source.mediaType().mimeType());
            attachment.setFileSize(source.size());
            attachment.setSha256(source.sha256());
            attachment.setDeleted(false);
            attachments.insert(attachment);

            AiExtractionFileEntity extractionFile = new AiExtractionFileEntity();
            extractionFile.setTaskId(task.getId());
            extractionFile.setAttachmentId(attachment.getId());
            extractionFile.setSortOrder(source.sortOrder());
            extractionFile.setFileRole("ORIGINAL");
            extractionFile.setImageWidth(source.imageWidth());
            extractionFile.setImageHeight(source.imageHeight());
            extractionFile.setPreprocessStatus("VALIDATED");
            files.insert(extractionFile);
        }
        return new CreatedExtraction(task.getId());
    }

    @Transactional
    public boolean saveProviderResult(Long taskId, AiExtractionProviderResult result) {
        AiExtractionTaskEntity task = requireTask(taskId);
        if (!AiTaskStatus.RUNNING.getValue().equals(task.getStatus())) return false;
        Map<Long, KnowledgeNodeReferenceEntity> validKnowledge = validKnowledge(result, task.getSubjectId());
        int sequence = 0;
        for (AiExtractedQuestion extracted : result.questions()) {
            AiExtractionQuestionEntity question = question(taskId, ++sequence, extracted);
            questions.insert(question);
            if (Boolean.TRUE.equals(task.getMatchKnowledge())) {
                insertCandidates(question.getId(), extracted.knowledgeCandidates(), validKnowledge);
            }
        }
        if (tasks.markReviewRequired(taskId) == 0) {
            throw new BusinessException("TASK_STATE_CONFLICT", "extraction task was cancelled",
                    HttpStatus.CONFLICT);
        }
        return true;
    }

    @Transactional(readOnly = true)
    public AiExtractionTaskEntity requireTask(Long taskId) {
        AiExtractionTaskEntity task = tasks.selectById(taskId);
        if (task == null) throw new ResourceNotFoundException("AI extraction task not found");
        return task;
    }

    @Transactional(readOnly = true)
    public List<StoredAttachmentView> storedAttachments(Long taskId) {
        requireTask(taskId);
        List<AiExtractionFileEntity> taskFiles = files.selectList(Wrappers.<AiExtractionFileEntity>lambdaQuery()
                .eq(AiExtractionFileEntity::getTaskId, taskId).orderByAsc(AiExtractionFileEntity::getSortOrder));
        if (taskFiles.isEmpty()) return List.of();
        Map<Long, AttachmentEntity> found = attachments.selectBatchIds(taskFiles.stream()
                .map(AiExtractionFileEntity::getAttachmentId).toList()).stream()
                .collect(Collectors.toMap(AttachmentEntity::getId, Function.identity()));
        List<StoredAttachmentView> result = new ArrayList<>();
        for (AiExtractionFileEntity taskFile : taskFiles) {
            AttachmentEntity attachment = found.get(taskFile.getAttachmentId());
            if (attachment == null) throw integrity();
            result.add(new StoredAttachmentView(taskFile.getSortOrder(), attachment.getFileName(),
                    attachment.getMimeType(), Path.of(attachment.getStoragePath())));
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<AiExtractionQuestionEntity> questions(Long taskId) {
        requireTask(taskId);
        return questions.selectList(Wrappers.<AiExtractionQuestionEntity>lambdaQuery()
                .eq(AiExtractionQuestionEntity::getTaskId, taskId)
                .orderByAsc(AiExtractionQuestionEntity::getSequenceNo).orderByAsc(AiExtractionQuestionEntity::getId));
    }

    @Transactional(readOnly = true)
    public Map<Long, List<AiExtractionQuestionKnowledgeEntity>> candidates(List<Long> questionIds) {
        if (questionIds.isEmpty()) return Map.of();
        return knowledgeLinks.selectList(Wrappers.<AiExtractionQuestionKnowledgeEntity>lambdaQuery()
                .in(AiExtractionQuestionKnowledgeEntity::getExtractionQuestionId, questionIds)
                .orderByDesc(AiExtractionQuestionKnowledgeEntity::getIsPrimary)
                .orderByAsc(AiExtractionQuestionKnowledgeEntity::getId)).stream()
                .collect(Collectors.groupingBy(AiExtractionQuestionKnowledgeEntity::getExtractionQuestionId,
                        java.util.LinkedHashMap::new, Collectors.toList()));
    }

    @Transactional
    public void resetTemporaryQuestions(Long taskId) {
        resetTemporaryQuestionsInternal(taskId);
    }

    @Transactional
    public void beginRetry(Long taskId, String expectedStatus, Long modelId, boolean reset) {
        AiExtractionTaskEntity task = requireTask(taskId);
        if (!expectedStatus.equals(task.getStatus())) throw stateConflict("extraction retry conflict");
        if (reset) resetTemporaryQuestionsInternal(taskId);
        if (tasks.retry(taskId, expectedStatus, modelId) == 0) throw stateConflict("extraction retry conflict");
    }

    private void resetTemporaryQuestionsInternal(Long taskId) {
        List<Long> ids = questions(taskId).stream().map(AiExtractionQuestionEntity::getId).toList();
        if (ids.isEmpty()) return;
        if (questions.selectCount(Wrappers.<AiExtractionQuestionEntity>lambdaQuery().in(AiExtractionQuestionEntity::getId, ids)
                .eq(AiExtractionQuestionEntity::getStatus, "SAVED")) > 0) {
            throw new BusinessException("BUSINESS_RULE_VIOLATION", "saved questions cannot be reset",
                    HttpStatus.CONFLICT);
        }
        corrections.delete(Wrappers.lambdaQuery(com.stdntedu.ai.extraction.entity.AiExtractionCorrectionEntity.class)
                .eq(com.stdntedu.ai.extraction.entity.AiExtractionCorrectionEntity::getTaskId, taskId));
        knowledgeLinks.delete(Wrappers.<AiExtractionQuestionKnowledgeEntity>lambdaQuery()
                .in(AiExtractionQuestionKnowledgeEntity::getExtractionQuestionId, ids));
        questions.delete(Wrappers.<AiExtractionQuestionEntity>lambdaQuery().in(AiExtractionQuestionEntity::getId, ids));
    }

    private AiExtractionQuestionEntity question(Long taskId, int sequence, AiExtractedQuestion extracted) {
        AiExtractionQuestionEntity question = new AiExtractionQuestionEntity();
        question.setTaskId(taskId);
        question.setSequenceNo(sequence);
        question.setPageNo(extracted.pageNumber());
        question.setQuestionNo(clean(extracted.questionNumber(), 64));
        question.setQuestionType(clean(extracted.questionType(), 32));
        question.setQuestionText(extracted.questionText());
        question.setStudentAnswer(extracted.studentAnswer());
        question.setCorrectAnswer(extracted.correctAnswer());
        question.setAnswerSource(clean(extracted.answerSource(), 32));
        question.setAnalysisText(extracted.analysisText());
        question.setAnalysisSource(clean(extracted.analysisSource(), 32));
        question.setErrorType(clean(extracted.errorType(), 32));
        question.setDifficulty(extracted.difficulty());
        question.setConfidence(extracted.confidence());
        question.setStatus("PENDING_REVIEW");
        question.setUserModified(false);
        question.setVersion(0);
        return question;
    }

    private void insertCandidates(Long questionId, List<AiKnowledgeCandidate> candidates,
            Map<Long, KnowledgeNodeReferenceEntity> validKnowledge) {
        Set<Long> linked = new HashSet<>();
        for (AiKnowledgeCandidate candidate : candidates) {
            Long requestedId = numericId(candidate.knowledgeId());
            KnowledgeNodeReferenceEntity node = requestedId == null ? null : validKnowledge.get(requestedId);
            Long knowledgeId = node == null || !linked.add(node.getId()) ? null : node.getId();
            AiExtractionQuestionKnowledgeEntity link = new AiExtractionQuestionKnowledgeEntity();
            link.setExtractionQuestionId(questionId);
            link.setKnowledgeId(knowledgeId);
            link.setKnowledgeCode(node == null ? clean(candidate.knowledgeCode(), 64) : node.getNodeCode());
            link.setKnowledgeName(node == null ? clean(candidate.knowledgeName(), 128) : node.getName());
            if (link.getKnowledgeName() == null) link.setKnowledgeName("Unmatched candidate");
            link.setConfidence(candidate.confidence());
            link.setIsPrimary(candidate.primary());
            link.setConfirmed(false);
            link.setSource("AI");
            knowledgeLinks.insert(link);
        }
    }

    private Map<Long, KnowledgeNodeReferenceEntity> validKnowledge(AiExtractionProviderResult result, Long subjectId) {
        Set<Long> requested = result.questions().stream().flatMap(q -> q.knowledgeCandidates().stream())
                .map(candidate -> numericId(candidate.knowledgeId())).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (requested.isEmpty()) return Map.of();
        return knowledgeNodes.selectBatchIds(requested).stream()
                .filter(node -> Boolean.TRUE.equals(node.getEnabled()))
                .filter(node -> subjectId == null || subjectId.equals(node.getSubjectId()))
                .collect(Collectors.toMap(KnowledgeNodeReferenceEntity::getId, Function.identity()));
    }

    private Long numericId(String value) {
        if (value == null || !value.matches("^[0-9]+$")) return null;
        try { return Long.valueOf(value); } catch (NumberFormatException ex) { return null; }
    }

    private Long optionalId(String value) {
        return value == null || value.isBlank() ? null : ids.toLong(value);
    }

    private String clean(String value, int maximum) {
        if (value == null) return null;
        String clean = value.trim();
        if (clean.isEmpty()) return null;
        return clean.length() <= maximum ? clean : clean.substring(0, maximum);
    }

    private BusinessException integrity() {
        return new BusinessException("DATA_INTEGRITY_ERROR", "AI extraction attachment reference is invalid",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private BusinessException stateConflict(String message) {
        return new BusinessException("TASK_STATE_CONFLICT", message, HttpStatus.CONFLICT);
    }
}
