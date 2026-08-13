package com.stdntedu.score.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stdntedu.base.entity.SubjectEntity;
import com.stdntedu.base.mapper.SubjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.Exam;
import com.stdntedu.generated.model.ExamCreate;
import com.stdntedu.generated.model.ExamType;
import com.stdntedu.generated.model.ExamUpdate;
import com.stdntedu.generated.model.ScoreKnowledgeInput;
import com.stdntedu.generated.model.ScoreListItemDto;
import com.stdntedu.generated.model.ScorePageResponseAllOfData;
import com.stdntedu.generated.model.ScoreTrendPointDto;
import com.stdntedu.generated.model.SubjectScore;
import com.stdntedu.generated.model.SubjectScoreDto;
import com.stdntedu.knowledge.mastery.service.MasteryService;
import com.stdntedu.score.converter.ExamConverter;
import com.stdntedu.score.converter.ScoreConverter;
import com.stdntedu.score.converter.ScoreKnowledgeConverter;
import com.stdntedu.score.entity.ExamEntity;
import com.stdntedu.score.entity.KnowledgeNodeReferenceEntity;
import com.stdntedu.score.entity.ScoreKnowledgeEntity;
import com.stdntedu.score.entity.ScoreRecordEntity;
import com.stdntedu.score.mapper.ExamMapper;
import com.stdntedu.score.mapper.KnowledgeNodeReferenceMapper;
import com.stdntedu.score.mapper.ScoreKnowledgeMapper;
import com.stdntedu.score.mapper.ScoreQueryMapper;
import com.stdntedu.score.mapper.ScoreRecordMapper;
import com.stdntedu.student.entity.AcademicTermEntity;
import com.stdntedu.student.entity.StudentEntity;
import com.stdntedu.student.mapper.AcademicTermMapper;
import com.stdntedu.student.mapper.StudentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamService {
    private final ExamMapper exams;
    private final ScoreRecordMapper scoreRecords;
    private final ScoreKnowledgeMapper scoreKnowledge;
    private final ScoreQueryMapper scoreQueries;
    private final KnowledgeNodeReferenceMapper knowledgeNodes;
    private final StudentMapper students;
    private final AcademicTermMapper terms;
    private final SubjectMapper subjects;
    private final ExamConverter examConverter;
    private final ScoreConverter scoreConverter;
    private final ScoreKnowledgeConverter scoreKnowledgeConverter;
    private final IdConverter ids;
    private final MasteryService mastery;

    public ExamService(ExamMapper exams, ScoreRecordMapper scoreRecords, ScoreKnowledgeMapper scoreKnowledge,
            KnowledgeNodeReferenceMapper knowledgeNodes, ScoreQueryMapper scoreQueries, StudentMapper students, AcademicTermMapper terms,
            SubjectMapper subjects, ExamConverter examConverter, ScoreConverter scoreConverter,
            ScoreKnowledgeConverter scoreKnowledgeConverter, IdConverter ids, MasteryService mastery) {
        this.exams = exams;
        this.scoreRecords = scoreRecords;
        this.scoreKnowledge = scoreKnowledge;
        this.knowledgeNodes = knowledgeNodes;
        this.scoreQueries = scoreQueries;
        this.students = students;
        this.terms = terms;
        this.subjects = subjects;
        this.examConverter = examConverter;
        this.scoreConverter = scoreConverter;
        this.scoreKnowledgeConverter = scoreKnowledgeConverter;
        this.ids = ids;
        this.mastery = mastery;
    }

    @Transactional
    public Exam create(ExamCreate request) {
        Long studentId = ids.toLong(request.getStudentId());
        validateRequest(request.getAcademicTermId(), request.getSubjects(), studentId);
        ExamEntity exam = examConverter.fromCreate(request);
        applyTotals(exam, request.getSubjects());
        exam.setDeleted(false);
        exam.setVersion(0);
        exams.insert(exam);
        replaceScoreRecords(exam, request.getSubjects(), Map.of());
        mastery.recalculateAffectedMastery(studentId, knowledgeIds(request.getSubjects()), "CREATE_EXAM", "EXAM",
                exam.getId());
        return getById(exam.getId());
    }

    @Transactional(readOnly = true)
    public Exam get(String examId) {
        return getById(ids.toLong(examId));
    }

    @Transactional
    public Exam update(String examId, ExamUpdate request) {
        Long id = ids.toLong(examId);
        ExamEntity exam = requireExam(id);
        Long previousStudentId = exam.getStudentId();
        Set<Long> previousKnowledgeIds = knowledgeIdsForExam(id);
        if (!Objects.equals(exam.getVersion(), request.getVersion())) {
            throw versionConflict();
        }
        Long studentId = ids.toLong(request.getStudentId());
        validateRequest(request.getAcademicTermId(), request.getSubjects(), studentId);
        examConverter.applyUpdate(request, exam);
        applyTotals(exam, request.getSubjects());
        if (exams.updateById(exam) == 0) throw versionConflict();

        Map<Long, ScoreRecordEntity> existing = scoreRecords.selectList(Wrappers.<ScoreRecordEntity>lambdaQuery()
                .eq(ScoreRecordEntity::getExamId, id)).stream()
                .collect(Collectors.toMap(ScoreRecordEntity::getSubjectId, Function.identity()));
        replaceScoreRecords(exam, request.getSubjects(), existing);
        Set<Long> newKnowledgeIds = knowledgeIds(request.getSubjects());
        if (Objects.equals(previousStudentId, studentId)) {
            Set<Long> affected = new HashSet<>(previousKnowledgeIds);
            affected.addAll(newKnowledgeIds);
            mastery.recalculateAffectedMastery(studentId, affected, "UPDATE_EXAM", "EXAM", id);
        } else {
            mastery.recalculateAffectedMastery(previousStudentId, previousKnowledgeIds, "UPDATE_EXAM", "EXAM", id);
            mastery.recalculateAffectedMastery(studentId, newKnowledgeIds, "UPDATE_EXAM", "EXAM", id);
        }
        return getById(id);
    }

    @Transactional
    public void delete(String examId) {
        Long id = ids.toLong(examId);
        ExamEntity exam = requireExam(id);
        Set<Long> affected = knowledgeIdsForExam(id);
        if (exams.deleteById(id) == 0) throw new ResourceNotFoundException("exam not found");
        scoreRecords.update(null, Wrappers.<ScoreRecordEntity>lambdaUpdate()
                .eq(ScoreRecordEntity::getExamId, id).set(ScoreRecordEntity::getDeleted, true));
        mastery.recalculateAffectedMastery(exam.getStudentId(), affected, "DELETE_EXAM", "EXAM", id);
    }

    @Transactional(readOnly = true)
    public ScorePageResponseAllOfData listScores(String studentId, String academicTermId, String subjectId,
            ExamType examType, LocalDate startDate, LocalDate endDate, String keyword, int page, int pageSize) {
        Long student = ids.toLong(studentId);
        requireStudent(student);
        validateDateRange(startDate, endDate);
        Long term = academicTermId == null ? null : ids.toLong(academicTermId);
        Long subject = subjectId == null ? null : ids.toLong(subjectId);
        String type = examType == null ? null : examType.getValue();
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        long total = scoreQueries.count(student, term, subject, type, startDate, endDate, normalizedKeyword);
        List<ScoreListItemDto> items = scoreQueries.selectPage(student, term, subject, type, startDate, endDate,
                normalizedKeyword, (long) (page - 1) * pageSize, pageSize).stream().map(scoreConverter::toListItem).toList();
        return pageResult(items, page, pageSize, total);
    }

    @Transactional(readOnly = true)
    public List<ScoreTrendPointDto> trends(String studentId, String subjectId, String academicTermId,
            LocalDate startDate, LocalDate endDate) {
        Long student = ids.toLong(studentId);
        requireStudent(student);
        validateDateRange(startDate, endDate);
        var examQuery = Wrappers.<ExamEntity>lambdaQuery().eq(ExamEntity::getStudentId, student)
                .orderByAsc(ExamEntity::getExamDate).orderByAsc(ExamEntity::getCreateTime);
        if (academicTermId != null) examQuery.eq(ExamEntity::getAcademicTermId, ids.toLong(academicTermId));
        if (startDate != null) examQuery.ge(ExamEntity::getExamDate, startDate);
        if (endDate != null) examQuery.le(ExamEntity::getExamDate, endDate);
        List<ExamEntity> matchingExams = exams.selectList(examQuery);
        if (subjectId == null) return matchingExams.stream().map(exam -> scoreConverter.toTrendPoint(exam, null)).toList();
        if (matchingExams.isEmpty()) return List.of();
        Long subject = ids.toLong(subjectId);
        Map<Long, ScoreRecordEntity> records = scoreRecords.selectList(Wrappers.<ScoreRecordEntity>lambdaQuery()
                .eq(ScoreRecordEntity::getSubjectId, subject)
                .in(ScoreRecordEntity::getExamId, matchingExams.stream().map(ExamEntity::getId).toList())).stream()
                .collect(Collectors.toMap(ScoreRecordEntity::getExamId, Function.identity()));
        return matchingExams.stream().filter(exam -> records.containsKey(exam.getId()))
                .map(exam -> scoreConverter.toTrendPoint(exam, records.get(exam.getId()))).toList();
    }

    private Exam getById(Long id) {
        ExamEntity exam = requireExam(id);
        List<ScoreRecordEntity> records = scoreRecords.selectList(Wrappers.<ScoreRecordEntity>lambdaQuery()
                .eq(ScoreRecordEntity::getExamId, id).orderByAsc(ScoreRecordEntity::getId));
        Map<Long, List<ScoreKnowledgeEntity>> knowledgeByRecord = knowledgeByRecord(records.stream()
                .map(ScoreRecordEntity::getId).toList());
        Map<Long, KnowledgeNodeReferenceEntity> nodes = knowledgeNodeMap(knowledgeByRecord.values().stream()
                .flatMap(Collection::stream).map(ScoreKnowledgeEntity::getKnowledgeId).toList());
        List<SubjectScoreDto> subjectDtos = records.stream().map(record -> {
            SubjectScoreDto dto = scoreConverter.toSubjectDto(record);
            dto.knowledgeScores(knowledgeByRecord.getOrDefault(record.getId(), List.of()).stream().map(detail -> {
                KnowledgeNodeReferenceEntity node = nodes.get(detail.getKnowledgeId());
                if (node == null) throw new ResourceNotFoundException("knowledge node not found");
                return scoreKnowledgeConverter.toDto(detail, node);
            }).toList());
            return dto;
        }).toList();
        Exam dto = examConverter.toDto(exam);
        dto.subjects(subjectDtos).totalScore(exam.getTotalScore()).totalFullScore(exam.getTotalFullScore())
                .totalScoreRate(scoreConverter.rate(exam.getTotalScore(), exam.getTotalFullScore()));
        return dto;
    }

    private void replaceScoreRecords(ExamEntity exam, List<SubjectScore> inputs, Map<Long, ScoreRecordEntity> existing) {
        Set<Long> retainedSubjects = new HashSet<>();
        for (SubjectScore input : inputs) {
            Long subjectId = ids.toLong(input.getSubjectId());
            retainedSubjects.add(subjectId);
            ScoreRecordEntity record = existing.get(subjectId);
            if (record == null) {
                record = scoreConverter.fromInput(input, exam.getId(), exam.getStudentId());
                record.setDeleted(false);
                record.setVersion(0);
                scoreRecords.insert(record);
            } else {
                scoreConverter.apply(input, record);
                record.setStudentId(exam.getStudentId());
                if (scoreRecords.updateById(record) == 0) throw versionConflict();
                scoreKnowledge.delete(Wrappers.<ScoreKnowledgeEntity>lambdaQuery()
                        .eq(ScoreKnowledgeEntity::getScoreRecordId, record.getId()));
            }
            writeKnowledge(record.getId(), input.getKnowledgeScores());
        }
        List<Long> removed = existing.values().stream().filter(record -> !retainedSubjects.contains(record.getSubjectId()))
                .map(ScoreRecordEntity::getId).toList();
        if (!removed.isEmpty()) {
            scoreRecords.update(null, Wrappers.<ScoreRecordEntity>lambdaUpdate().in(ScoreRecordEntity::getId, removed)
                    .set(ScoreRecordEntity::getDeleted, true));
        }
    }

    private void writeKnowledge(Long scoreRecordId, List<ScoreKnowledgeInput> inputs) {
        if (inputs == null) return;
        for (ScoreKnowledgeInput input : inputs) scoreKnowledge.insert(scoreKnowledgeConverter.fromInput(input, scoreRecordId));
    }

    private void validateRequest(String academicTermId, List<SubjectScore> scoreInputs, Long studentId) {
        requireStudent(studentId);
        if (academicTermId != null) {
            AcademicTermEntity term = terms.selectById(ids.toLong(academicTermId));
            if (term == null || !Objects.equals(term.getStudentId(), studentId)) {
                throw validation("academic term does not belong to student");
            }
        }
        if (scoreInputs == null || scoreInputs.isEmpty()) throw validation("at least one subject score is required");
        Set<Long> subjectIds = new HashSet<>();
        for (SubjectScore input : scoreInputs) {
            Long subjectId = ids.toLong(input.getSubjectId());
            if (!subjectIds.add(subjectId)) throw validation("duplicate subject score");
            validateSubjectScore(input);
        }
        Map<Long, SubjectEntity> subjectById = subjectMap(subjectIds);
        if (subjectById.size() != subjectIds.size() || subjectById.values().stream().anyMatch(subject -> !Boolean.TRUE.equals(subject.getEnabled()))) {
            throw validation("subject does not exist or is disabled");
        }
        for (SubjectScore input : scoreInputs) validateKnowledgeScores(input, ids.toLong(input.getSubjectId()));
    }

    private void validateSubjectScore(SubjectScore input) {
        if (input.getScore() == null || input.getScore().signum() < 0 || input.getFullScore() == null
                || input.getFullScore().signum() <= 0 || input.getScore().compareTo(input.getFullScore()) > 0) {
            throw validation("score must be between zero and fullScore");
        }
        validateRank(input.getClassRank(), input.getClassSize(), "class");
        validateRank(input.getGradeRank(), input.getGradeSize(), "grade");
    }

    private void validateRank(Integer rank, Integer size, String scope) {
        if (rank == null) return;
        if (size == null || size < 1 || rank < 1 || rank > size) {
            throw validation(scope + " rank must be within its size");
        }
    }

    private void validateKnowledgeScores(SubjectScore subjectScore, Long subjectId) {
        List<ScoreKnowledgeInput> inputs = subjectScore.getKnowledgeScores();
        if (inputs == null || inputs.isEmpty()) return;
        Set<Long> idsInSubject = new HashSet<>();
        List<Long> knowledgeIds = new ArrayList<>();
        for (ScoreKnowledgeInput input : inputs) {
            Long knowledgeId = ids.toLong(input.getKnowledgeId());
            if (!idsInSubject.add(knowledgeId)) throw validation("duplicate knowledge score");
            knowledgeIds.add(knowledgeId);
            validateKnowledgeNumbers(input);
        }
        Map<Long, KnowledgeNodeReferenceEntity> nodeById = knowledgeNodeMap(knowledgeIds);
        if (nodeById.size() != knowledgeIds.size() || nodeById.values().stream().anyMatch(node ->
                !Boolean.TRUE.equals(node.getEnabled()) || !Objects.equals(node.getSubjectId(), subjectId))) {
            throw validation("knowledge node does not exist, is disabled, or does not belong to subject");
        }
    }

    private void validateKnowledgeNumbers(ScoreKnowledgeInput input) {
        if (input.getScore() == null || input.getScore().signum() < 0 || input.getFullScore() == null
                || input.getFullScore().signum() <= 0 || input.getScore().compareTo(input.getFullScore()) > 0
                || input.getQuestionCount() == null || input.getQuestionCount() < 0 || input.getCorrectCount() == null
                || input.getCorrectCount() < 0 || input.getCorrectCount() > input.getQuestionCount()) {
            throw validation("invalid knowledge score values");
        }
    }

    private void applyTotals(ExamEntity exam, List<SubjectScore> inputs) {
        BigDecimal totalScore = inputs.stream().map(SubjectScore::getScore).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFullScore = inputs.stream().map(SubjectScore::getFullScore).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        exam.setTotalScore(totalScore);
        exam.setTotalFullScore(totalFullScore);
    }

    private ExamEntity requireExam(Long id) {
        ExamEntity exam = exams.selectById(id);
        if (exam == null) throw new ResourceNotFoundException("exam not found");
        return exam;
    }

    private void requireStudent(Long id) {
        if (students.selectById(id) == null) throw new ResourceNotFoundException("student not found");
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw validation("endDate cannot be before startDate");
        }
    }

    private Map<Long, SubjectEntity> subjectMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return subjects.selectBatchIds(ids).stream().collect(Collectors.toMap(SubjectEntity::getId, Function.identity()));
    }

    private Map<Long, KnowledgeNodeReferenceEntity> knowledgeNodeMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return knowledgeNodes.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(KnowledgeNodeReferenceEntity::getId, Function.identity()));
    }

    private Map<Long, List<ScoreKnowledgeEntity>> knowledgeByRecord(List<Long> recordIds) {
        if (recordIds.isEmpty()) return Map.of();
        return scoreKnowledge.selectList(Wrappers.<ScoreKnowledgeEntity>lambdaQuery()
                .in(ScoreKnowledgeEntity::getScoreRecordId, recordIds)).stream()
                .collect(Collectors.groupingBy(ScoreKnowledgeEntity::getScoreRecordId));
    }

    private Set<Long> knowledgeIds(List<SubjectScore> inputs) {
        if (inputs == null) return Set.of();
        return inputs.stream().filter(Objects::nonNull).map(SubjectScore::getKnowledgeScores)
                .filter(Objects::nonNull).flatMap(Collection::stream).map(ScoreKnowledgeInput::getKnowledgeId)
                .map(ids::toLong).collect(Collectors.toSet());
    }

    private Set<Long> knowledgeIdsForExam(Long examId) {
        List<Long> recordIds = scoreRecords.selectList(Wrappers.<ScoreRecordEntity>lambdaQuery()
                .eq(ScoreRecordEntity::getExamId, examId)).stream().map(ScoreRecordEntity::getId).toList();
        if (recordIds.isEmpty()) return Set.of();
        return scoreKnowledge.selectList(Wrappers.<ScoreKnowledgeEntity>lambdaQuery()
                .in(ScoreKnowledgeEntity::getScoreRecordId, recordIds)).stream()
                .map(ScoreKnowledgeEntity::getKnowledgeId).collect(Collectors.toSet());
    }

    private String subjectName(Map<Long, SubjectEntity> subjects, Long subjectId) {
        SubjectEntity subject = subjects.get(subjectId);
        return subject == null ? null : subject.getName();
    }

    private ScorePageResponseAllOfData pageResult(List<ScoreListItemDto> items, int page, int pageSize, long total) {
        int totalPages = pageSize == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
        return new ScorePageResponseAllOfData().items(items).page(page).pageSize(pageSize).total(total)
                .totalPages(totalPages);
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BusinessException versionConflict() {
        return new BusinessException("DATA_VERSION_CONFLICT", "exam version conflict", HttpStatus.CONFLICT);
    }
}
