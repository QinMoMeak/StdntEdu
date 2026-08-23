package com.stdntedu.knowledge.mastery.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.InlineObject22AllOfData;
import com.stdntedu.generated.model.MasteryAdjustRequest;
import com.stdntedu.generated.model.MasteryDto;
import com.stdntedu.knowledge.mastery.algorithm.MasteryAlgorithmConfig;
import com.stdntedu.knowledge.mastery.algorithm.MasteryCalculationResult;
import com.stdntedu.knowledge.mastery.algorithm.MasteryReplayEngine;
import com.stdntedu.knowledge.mastery.converter.MasteryConverter;
import com.stdntedu.knowledge.mastery.entity.MasteryHistoryEntity;
import com.stdntedu.knowledge.mastery.entity.StudentMasteryEntity;
import com.stdntedu.knowledge.mastery.evidence.MasteryEvidenceAggregator;
import com.stdntedu.knowledge.mastery.evidence.MasteryEvidenceAggregator.AggregatedEvidence;
import com.stdntedu.knowledge.mastery.mapper.MasteryHistoryMapper;
import com.stdntedu.knowledge.mastery.mapper.StudentMasteryMapper;
import com.stdntedu.score.entity.KnowledgeNodeReferenceEntity;
import com.stdntedu.score.mapper.KnowledgeNodeReferenceMapper;
import com.stdntedu.student.mapper.StudentMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasteryService {
    private static final BigDecimal HISTORY_INITIAL_BASELINE = new BigDecimal("0.00");

    private final StudentMasteryMapper mastery;
    private final MasteryHistoryMapper history;
    private final MasteryEvidenceAggregator evidence;
    private final MasteryAlgorithmConfigService configService;
    private final MasteryReplayEngine replay;
    private final StudentMapper students;
    private final KnowledgeNodeReferenceMapper knowledge;
    private final MasteryConverter converter;
    private final IdConverter ids;
    private final ObjectMapper objectMapper;

    public MasteryService(StudentMasteryMapper mastery, MasteryHistoryMapper history,
            MasteryEvidenceAggregator evidence, MasteryAlgorithmConfigService configService,
            MasteryReplayEngine replay, StudentMapper students, KnowledgeNodeReferenceMapper knowledge,
            MasteryConverter converter, IdConverter ids, ObjectMapper objectMapper) {
        this.mastery = mastery;
        this.history = history;
        this.evidence = evidence;
        this.configService = configService;
        this.replay = replay;
        this.students = students;
        this.knowledge = knowledge;
        this.converter = converter;
        this.ids = ids;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<MasteryDto> list(String studentId, String subjectId, String gradeId, BigDecimal minScore,
            BigDecimal maxScore) {
        Long student = ids.toLong(studentId);
        requireStudent(student);
        if (minScore != null && maxScore != null && minScore.compareTo(maxScore) > 0) {
            throw validation("minScore cannot exceed maxScore");
        }
        Long subject = subjectId == null ? null : ids.toLong(subjectId);
        Long grade = gradeId == null ? null : ids.toLong(gradeId);
        ZoneId zone = configService.load().timezone();
        return mastery.selectForList(student, subject, grade, minScore, maxScore).stream()
                .map(item -> converter.toDto(item, zone)).toList();
    }

    @Transactional(readOnly = true)
    public InlineObject22AllOfData history(String studentId, String knowledgeId, int page, int pageSize) {
        Long student = ids.toLong(studentId);
        Long node = ids.toLong(knowledgeId);
        requireStudent(student);
        requireKnowledge(node, false);
        Page<MasteryHistoryEntity> result = history.selectPage(new Page<>(page, pageSize),
                Wrappers.<MasteryHistoryEntity>lambdaQuery().eq(MasteryHistoryEntity::getStudentId, student)
                        .eq(MasteryHistoryEntity::getKnowledgeId, node)
                        .orderByDesc(MasteryHistoryEntity::getCreateTime).orderByDesc(MasteryHistoryEntity::getId));
        ZoneId zone = configService.load().timezone();
        return new InlineObject22AllOfData().page(page).pageSize(pageSize).total(result.getTotal())
                .totalPages((int) result.getPages())
                .items(result.getRecords().stream().map(item -> converter.toDto(item, zone)).toList());
    }

    @Transactional
    public MasteryDto adjust(String knowledgeId, MasteryAdjustRequest request) {
        Long student = ids.toLong(request.getStudentId());
        Long node = ids.toLong(knowledgeId);
        requireStudent(student);
        requireKnowledge(node, true);
        validateAdjustment(request);
        MasteryAlgorithmConfig config = configService.load();
        StudentMasteryEntity current = find(student, node);
        BigDecimal target = normalize(request.getTargetScore());
        BigDecimal before;
        if (current == null) {
            if (request.getVersion() != 0) throw versionConflict();
            AggregatedEvidence aggregate = evidence.aggregate(student, node, config);
            MasteryCalculationResult calculated = replay.replay(aggregate.items(), aggregate.nextReviewTime(), config);
            current = new StudentMasteryEntity();
            current.setStudentId(student);
            current.setKnowledgeId(node);
            current.setMasteryScore(target);
            current.setManualLocked(true);
            current.setVersion(0);
            applyStatistics(current, calculated);
            mastery.insert(current);
            // No prior score exists. Using target as both sides records creation without inventing a baseline.
            before = target;
        } else {
            if (!Objects.equals(current.getVersion(), request.getVersion())) throw versionConflict();
            before = current.getMasteryScore();
            current.setMasteryScore(target);
            current.setManualLocked(true);
            if (mastery.updateById(current) == 0) throw versionConflict();
        }
        writeHistory(current, "MANUAL_ADJUST", "MANUAL", null, before, target, true,
                request.getReason().trim(), detail(config, "MANUAL_ADJUST", "MANUAL", null, before, target, target,
                        current.getEvidenceCount(), true, true, Map.of()));
        return converter.toDto(requireCurrent(student, node), config.timezone());
    }

    @Transactional
    public MasteryDto unlock(String studentId, String knowledgeId) {
        Long student = ids.toLong(studentId);
        Long node = ids.toLong(knowledgeId);
        requireStudent(student);
        requireKnowledge(node, true);
        StudentMasteryEntity current = requireCurrent(student, node);
        if (!Boolean.TRUE.equals(current.getManualLocked())) {
            throw conflict("mastery is not manually locked");
        }
        MasteryAlgorithmConfig config = configService.load();
        AggregatedEvidence aggregate = evidence.aggregate(student, node, config);
        MasteryCalculationResult calculated = replay.replay(aggregate.items(), aggregate.nextReviewTime(), config);
        if (!calculated.hasEvidence()) throw conflict("mastery cannot be unlocked without valid evidence");
        BigDecimal before = current.getMasteryScore();
        current.setManualLocked(false);
        current.setMasteryScore(calculated.masteryScore());
        applyStatistics(current, calculated);
        if (mastery.updateById(current) == 0) throw versionConflict();
        writeHistory(current, "MANUAL_UNLOCK", "MANUAL", null, before, calculated.masteryScore(), true,
                "manual unlock", detail(config, "MANUAL_UNLOCK", "MANUAL", null, before,
                        calculated.masteryScore(), calculated.masteryScore(), calculated.evidenceCount(), true, false,
                        calculated.evidenceSummary()));
        return converter.toDto(requireCurrent(student, node), config.timezone());
    }

    @Transactional
    public void recalculateMastery(Long studentId, Long knowledgeId, String trigger, String businessType,
            Long businessId) {
        MasteryAlgorithmConfig config = configService.load();
        recalculateOne(studentId, knowledgeId, trigger, businessType, businessId, config);
    }

    @Transactional
    public void recalculateAffectedMastery(Long studentId, Collection<Long> knowledgeIds, String trigger,
            String businessType, Long businessId) {
        if (knowledgeIds == null || knowledgeIds.isEmpty()) return;
        MasteryAlgorithmConfig config = configService.load();
        for (Long knowledgeId : new TreeSet<>(Set.copyOf(knowledgeIds))) {
            recalculateOne(studentId, knowledgeId, trigger, businessType, businessId, config);
        }
    }

    private void recalculateOne(Long studentId, Long knowledgeId, String trigger, String businessType,
            Long businessId, MasteryAlgorithmConfig config) {
        AggregatedEvidence aggregate = evidence.aggregate(studentId, knowledgeId, config);
        MasteryCalculationResult calculated = replay.replay(aggregate.items(), aggregate.nextReviewTime(), config);
        for (int attempt = 0; attempt < 2; attempt++) {
            StudentMasteryEntity current = find(studentId, knowledgeId);
            AutomaticChange change = persistAutomatic(current, studentId, knowledgeId, calculated);
            if (change.retry()) continue;
            if (change.scoreChanged()) {
                writeHistory(change.entity(), "AUTO_REPLAY", businessType, businessId, change.before(), change.after(),
                        false, null, detail(config, trigger, businessType, businessId, change.before(),
                                calculated.masteryScore(), change.after(), calculated.evidenceCount(),
                                current != null && Boolean.TRUE.equals(current.getManualLocked()),
                                change.entity() != null && Boolean.TRUE.equals(change.entity().getManualLocked()),
                                calculated.evidenceSummary()));
            }
            return;
        }
        throw versionConflict();
    }

    private AutomaticChange persistAutomatic(StudentMasteryEntity current, Long studentId, Long knowledgeId,
            MasteryCalculationResult calculated) {
        if (!calculated.hasEvidence()) {
            if (current == null) return AutomaticChange.noChange();
            if (!Boolean.TRUE.equals(current.getManualLocked())) {
                return mastery.deleteWithVersion(current.getId(), current.getVersion()) == 1
                        ? AutomaticChange.noChange() : AutomaticChange.retryChange();
            }
            BigDecimal score = current.getMasteryScore();
            if (samePersistedState(current, score, calculated, true)) return AutomaticChange.noChange();
            applyStatistics(current, calculated);
            return mastery.updateById(current) == 1 ? AutomaticChange.noChange() : AutomaticChange.retryChange();
        }

        if (current == null) {
            StudentMasteryEntity created = new StudentMasteryEntity();
            created.setStudentId(studentId);
            created.setKnowledgeId(knowledgeId);
            created.setMasteryScore(calculated.masteryScore());
            created.setManualLocked(false);
            created.setVersion(0);
            applyStatistics(created, calculated);
            try {
                mastery.insert(created);
                return new AutomaticChange(created, HISTORY_INITIAL_BASELINE, calculated.masteryScore(), true, false);
            } catch (DuplicateKeyException ex) {
                return AutomaticChange.retryChange();
            }
        }

        boolean locked = Boolean.TRUE.equals(current.getManualLocked());
        BigDecimal before = current.getMasteryScore();
        BigDecimal applied = locked ? before : calculated.masteryScore();
        if (samePersistedState(current, applied, calculated, locked)) return AutomaticChange.noChange();
        current.setMasteryScore(applied);
        current.setManualLocked(locked);
        applyStatistics(current, calculated);
        if (mastery.updateById(current) == 0) return AutomaticChange.retryChange();
        return new AutomaticChange(current, before, applied, before.compareTo(applied) != 0, false);
    }

    private boolean samePersistedState(StudentMasteryEntity current, BigDecimal applied,
            MasteryCalculationResult result, boolean locked) {
        return decimalEquals(current.getMasteryScore(), applied)
                && Objects.equals(current.getCorrectCount(), result.correctCount())
                && Objects.equals(current.getPartialCount(), result.partialCount())
                && Objects.equals(current.getWrongCount(), result.wrongCount())
                && Objects.equals(current.getReviewCount(), result.reviewCount())
                && Objects.equals(current.getContinuousCorrectCount(), result.continuousCorrectCount())
                && Objects.equals(current.getContinuousWrongCount(), result.continuousWrongCount())
                && Objects.equals(current.getEvidenceCount(), result.evidenceCount())
                && Objects.equals(current.getLastPracticeTime(), result.lastPracticeTime())
                && Objects.equals(current.getLastReviewTime(), result.lastReviewTime())
                && Objects.equals(current.getNextReviewTime(), result.nextReviewTime())
                && Objects.equals(current.getDecayBaseTime(), result.decayBaseTime())
                && Objects.equals(current.getManualLocked(), locked);
    }

    private void applyStatistics(StudentMasteryEntity entity, MasteryCalculationResult result) {
        entity.setCorrectCount(result.correctCount());
        entity.setPartialCount(result.partialCount());
        entity.setWrongCount(result.wrongCount());
        entity.setReviewCount(result.reviewCount());
        entity.setContinuousCorrectCount(result.continuousCorrectCount());
        entity.setContinuousWrongCount(result.continuousWrongCount());
        entity.setEvidenceCount(result.evidenceCount());
        entity.setLastPracticeTime(result.lastPracticeTime());
        entity.setLastReviewTime(result.lastReviewTime());
        entity.setNextReviewTime(result.nextReviewTime());
        entity.setDecayBaseTime(result.decayBaseTime());
    }

    private void writeHistory(StudentMasteryEntity current, String eventType, String businessType, Long businessId,
            BigDecimal before, BigDecimal after, boolean manual, String remark, Map<String, Object> detail) {
        MasteryHistoryEntity item = new MasteryHistoryEntity();
        item.setStudentId(current.getStudentId());
        item.setKnowledgeId(current.getKnowledgeId());
        item.setEventType(eventType);
        item.setBusinessType(businessType);
        item.setBusinessId(businessId);
        item.setScoreBefore(normalize(before));
        item.setScoreAfter(normalize(after));
        item.setChangeValue(normalize(after).subtract(normalize(before)).setScale(2, RoundingMode.HALF_UP));
        item.setCalculationDetailJson(json(detail));
        item.setManualFlag(manual);
        item.setRemark(remark);
        history.insert(item);
    }

    private Map<String, Object> detail(MasteryAlgorithmConfig config, String trigger, String businessType,
            Long businessId, BigDecimal original, BigDecimal calculated, BigDecimal applied, int evidenceCount,
            boolean lockedBefore, boolean lockedAfter, Map<String, Long> summary) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("algorithmVersion", config.algorithmVersion());
        detail.put("trigger", trigger);
        detail.put("businessType", businessType);
        detail.put("businessId", businessId);
        detail.put("evidenceCount", evidenceCount);
        detail.put("correctCount", summary.getOrDefault("CORRECT", 0L));
        detail.put("partialCount", summary.getOrDefault("PARTIAL", 0L));
        detail.put("wrongCount", summary.getOrDefault("WRONG", 0L));
        detail.put("reviewCount", summary.getOrDefault("REVIEW", 0L));
        detail.put("originalScore", original);
        detail.put("calculatedScore", calculated);
        detail.put("appliedScore", applied);
        detail.put("manualLockedBefore", lockedBefore);
        detail.put("manualLockedAfter", lockedAfter);
        detail.put("configSnapshot", config.snapshot());
        return detail;
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot serialize mastery calculation detail", ex);
        }
    }

    private StudentMasteryEntity find(Long studentId, Long knowledgeId) {
        return mastery.selectOne(Wrappers.<StudentMasteryEntity>lambdaQuery()
                .eq(StudentMasteryEntity::getStudentId, studentId)
                .eq(StudentMasteryEntity::getKnowledgeId, knowledgeId));
    }

    private StudentMasteryEntity requireCurrent(Long studentId, Long knowledgeId) {
        StudentMasteryEntity current = find(studentId, knowledgeId);
        if (current == null) throw new ResourceNotFoundException("student mastery not found");
        return current;
    }

    private void requireStudent(Long studentId) {
        if (students.selectById(studentId) == null) throw new ResourceNotFoundException("student not found");
    }

    private KnowledgeNodeReferenceEntity requireKnowledge(Long knowledgeId, boolean enabledRequired) {
        KnowledgeNodeReferenceEntity node = knowledge.selectById(knowledgeId);
        if (node == null || (enabledRequired && !Boolean.TRUE.equals(node.getEnabled()))) {
            throw new ResourceNotFoundException("knowledge node not found");
        }
        return node;
    }

    private void validateAdjustment(MasteryAdjustRequest request) {
        if (!Boolean.TRUE.equals(request.getLockResult())) throw validation("lockResult must be true for algorithm V1.0");
        if (request.getTargetScore() == null || request.getTargetScore().signum() < 0
                || request.getTargetScore().compareTo(new BigDecimal("100")) > 0) {
            throw validation("targetScore must be between 0 and 100");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) throw validation("reason is required");
        if (request.getVersion() == null || request.getVersion() < 0) throw validation("version is required");
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean decimalEquals(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BusinessException conflict(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.CONFLICT);
    }

    private BusinessException versionConflict() {
        return new BusinessException("DATA_VERSION_CONFLICT", "mastery version conflict", HttpStatus.CONFLICT);
    }

    private record AutomaticChange(StudentMasteryEntity entity, BigDecimal before, BigDecimal after,
            boolean scoreChanged, boolean retry) {
        static AutomaticChange noChange() { return new AutomaticChange(null, null, null, false, false); }
        static AutomaticChange retryChange() { return new AutomaticChange(null, null, null, false, true); }
    }
}
