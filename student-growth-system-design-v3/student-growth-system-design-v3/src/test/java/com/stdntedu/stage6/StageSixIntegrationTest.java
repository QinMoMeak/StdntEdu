package com.stdntedu.stage6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.base.entity.SubjectEntity;
import com.stdntedu.base.mapper.SubjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.generated.model.Exam;
import com.stdntedu.generated.model.ExamCreate;
import com.stdntedu.generated.model.ExamType;
import com.stdntedu.generated.model.ExamUpdate;
import com.stdntedu.generated.model.KnowledgeLink;
import com.stdntedu.generated.model.MasteryAdjustRequest;
import com.stdntedu.generated.model.MasteryDto;
import com.stdntedu.generated.model.ReviewCreate;
import com.stdntedu.generated.model.ReviewResult;
import com.stdntedu.generated.model.ScoreKnowledgeInput;
import com.stdntedu.generated.model.StudentCreate;
import com.stdntedu.generated.model.SubjectScore;
import com.stdntedu.generated.model.Wrong;
import com.stdntedu.generated.model.WrongCreate;
import com.stdntedu.generated.model.WrongSource;
import com.stdntedu.generated.model.WrongStatus;
import com.stdntedu.generated.model.WrongUpdate;
import com.stdntedu.knowledge.mastery.algorithm.MasteryAlgorithmConfig;
import com.stdntedu.knowledge.mastery.entity.MasteryHistoryEntity;
import com.stdntedu.knowledge.mastery.entity.StudentMasteryEntity;
import com.stdntedu.knowledge.mastery.mapper.MasteryHistoryMapper;
import com.stdntedu.knowledge.mastery.mapper.StudentMasteryMapper;
import com.stdntedu.knowledge.mastery.service.MasteryAlgorithmConfigService;
import com.stdntedu.knowledge.mastery.service.MasteryService;
import com.stdntedu.score.entity.KnowledgeNodeReferenceEntity;
import com.stdntedu.score.mapper.KnowledgeNodeReferenceMapper;
import com.stdntedu.score.service.ExamService;
import com.stdntedu.student.service.StudentService;
import com.stdntedu.wrongquestion.entity.WrongQuestionEntity;
import com.stdntedu.wrongquestion.mapper.WrongQuestionMapper;
import com.stdntedu.wrongquestion.service.WrongQuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class StageSixIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.36").asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("student_growth").withUsername("student_growth").withPassword("student_growth");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired StudentService students;
    @Autowired SubjectMapper subjects;
    @Autowired KnowledgeNodeReferenceMapper nodes;
    @Autowired ExamService exams;
    @Autowired WrongQuestionService wrongQuestions;
    @Autowired WrongQuestionMapper wrongQuestionMapper;
    @Autowired MasteryService mastery;
    @Autowired MasteryAlgorithmConfigService configService;
    @Autowired StudentMasteryMapper masteryMapper;
    @Autowired MasteryHistoryMapper historyMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test void loadsAllMasteryConfigurationFromThirtyOneBaselineRows() {
        MasteryAlgorithmConfig config = configService.load();
        assertThat(jdbc.queryForObject("select count(*) from system_config", Integer.class)).isEqualTo(31);
        assertThat(config.algorithmVersion()).isEqualTo("1.0");
        assertThat(config.correctMinimum()).isEqualByComparingTo("0.80");
        assertThat(config.partialMinimum()).isEqualByComparingTo("0.60");
        assertThat(config.timeDecayEnabled()).isFalse();
    }

    @Test @Transactional void missingRequiredConfigurationFailsExplicitly() {
        jdbc.update("delete from system_config where config_key='mastery.initial.correct'");
        assertThatThrownBy(configService::load).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing mastery algorithm configuration");
    }

    @Test @Transactional void enabledTimeDecayIsRejectedAsUnsupported() {
        jdbc.update("update system_config set config_value='true' where config_key='mastery.time_decay.enabled'");
        assertThatThrownBy(configService::load).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("time decay is enabled");
    }

    @Test void examThresholdsInitializeCorrectPartialAndWrongScores() {
        String subject = subject();
        String correctStudent = student(), partialStudent = student(), wrongStudent = student();
        KnowledgeNodeReferenceEntity correct = node(subject), partial = node(subject), wrong = node(subject);
        createExam(correctStudent, subject, correct, "80", "100", LocalDate.of(2026, 1, 1));
        createExam(partialStudent, subject, partial, "60", "100", LocalDate.of(2026, 1, 1));
        createExam(wrongStudent, subject, wrong, "59", "100", LocalDate.of(2026, 1, 1));
        assertThat(current(correctStudent, correct).getMasteryScore()).isEqualByComparingTo("60.00");
        assertThat(current(partialStudent, partial).getMasteryScore()).isEqualByComparingTo("45.00");
        assertThat(current(wrongStudent, wrong).getMasteryScore()).isEqualByComparingTo("30.00");
    }

    @Test void examScoreRateTakesPriorityOverQuestionCorrectRate() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        createExam(student, subject, node, "50", "100", 10, 10, LocalDate.of(2026, 1, 1));
        assertThat(current(student, node).getMasteryScore()).isEqualByComparingTo("30.00");
    }

    @Test void examFallsBackToQuestionRateAndOmitsEvidenceWhenBothRatesUnavailable() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        createExam(student, subject, node, "50", "100", 10, 8, LocalDate.of(2026, 1, 1));
        Long detailId = jdbc.queryForObject("select max(id) from score_knowledge", Long.class);
        jdbc.update("update score_knowledge set score=null, full_score=null where id=?", detailId);
        mastery.recalculateMastery(Long.valueOf(student), node.getId(), "TEST_FALLBACK", "EXAM", detailId);
        assertThat(current(student, node).getMasteryScore()).isEqualByComparingTo("60.00");
        jdbc.update("update score_knowledge set correct_count=null, question_count=null where id=?", detailId);
        mastery.recalculateMastery(Long.valueOf(student), node.getId(), "TEST_INVALID", "EXAM", detailId);
        assertThat(find(student, node)).isNull();
    }

    @Test void practiceCreatesWrongEvidenceForEveryLinkedKnowledgeNode() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity first = node(subject), second = node(subject);
        createWrong(student, subject, List.of(link(first), link(second)));
        assertThat(current(student, first).getMasteryScore()).isEqualByComparingTo("30.00");
        assertThat(current(student, second).getMasteryScore()).isEqualByComparingTo("30.00");
        assertThat(current(student, first).getEvidenceCount()).isOne();
        assertThat(current(student, second).getEvidenceCount()).isOne();
    }

    @Test void reviewResultsApplyConfiguredDeltasAndUnknownCountsAsReviewOnly() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        Wrong question = createWrong(student, subject, List.of(link(node)));
        wrongQuestions.review(question.getId(), key(), review(ReviewResult.CORRECT, 1));
        assertThat(current(student, node).getMasteryScore()).isEqualByComparingTo("38.00");
        wrongQuestions.review(question.getId(), key(), review(ReviewResult.PARTIAL, 2));
        assertThat(current(student, node).getMasteryScore()).isEqualByComparingTo("41.00");
        wrongQuestions.review(question.getId(), key(), review(ReviewResult.WRONG, 3));
        assertThat(current(student, node).getMasteryScore()).isEqualByComparingTo("31.00");
        wrongQuestions.review(question.getId(), key(), review(ReviewResult.UNKNOWN, 4));
        StudentMasteryEntity stored = current(student, node);
        assertThat(stored.getMasteryScore()).isEqualByComparingTo("17.00");
        assertThat(stored.getReviewCount()).isEqualTo(4);
        assertThat(stored.getWrongCount()).isEqualTo(2);
    }

    @Test void archivedQuestionKeepsEvidenceButIsExcludedFromNextReviewTime() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        Wrong question = createWrong(student, subject, List.of(link(node)));
        assertThat(current(student, node).getNextReviewTime()).isNotNull();
        Wrong archived = wrongQuestions.update(question.getId(), update(question, subject, List.of(link(node)))
                .status(WrongStatus.ARCHIVED));
        StudentMasteryEntity stored = current(student, node);
        assertThat(stored.getEvidenceCount()).isOne();
        assertThat(stored.getNextReviewTime()).isNull();
        assertThat(archived.getStatus()).isEqualTo(WrongStatus.ARCHIVED);
    }

    @Test void deletingQuestionExcludesPracticeAndItsReviewChain() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        Wrong question = createWrong(student, subject, List.of(link(node)));
        wrongQuestions.review(question.getId(), key(), review(ReviewResult.CORRECT, 1));
        assertThat(current(student, node).getEvidenceCount()).isEqualTo(2);
        wrongQuestions.delete(question.getId());
        assertThat(find(student, node)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from wrong_review where wrong_question_id=?", Integer.class,
                Long.valueOf(question.getId()))).isOne();
    }

    @Test void nextReviewTimeUsesEarliestActiveRelatedQuestion() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        Wrong first = createWrong(student, subject, List.of(link(node)));
        Wrong second = createWrong(student, subject, List.of(link(node)));
        LocalDateTime early = LocalDateTime.of(2026, 2, 1, 8, 0), late = early.plusDays(2);
        setNextReview(first, late);
        setNextReview(second, early);
        mastery.recalculateMastery(Long.valueOf(student), node.getId(), "TEST_NEXT", "WRONG_QUESTION", null);
        assertThat(current(student, node).getNextReviewTime()).isEqualTo(early);
    }

    @Test void automaticReplayUpdatesOneUniqueRowAndIncrementsVersionOnlyWhenStateChanges() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        createExam(student, subject, node, "90", "100", LocalDate.of(2026, 1, 1));
        StudentMasteryEntity first = current(student, node);
        mastery.recalculateMastery(Long.valueOf(student), node.getId(), "REPEAT", "EXAM", null);
        StudentMasteryEntity unchanged = current(student, node);
        assertThat(unchanged.getId()).isEqualTo(first.getId());
        assertThat(unchanged.getVersion()).isEqualTo(first.getVersion());
        createExam(student, subject, node, "90", "100", LocalDate.of(2026, 1, 2));
        assertThat(current(student, node).getVersion()).isGreaterThan(first.getVersion());
        assertThat(masteryMapper.selectCount(Wrappers.<StudentMasteryEntity>lambdaQuery()
                .eq(StudentMasteryEntity::getStudentId, Long.valueOf(student))
                .eq(StudentMasteryEntity::getKnowledgeId, node.getId()))).isOne();
    }

    @Test void masteryRowsAreStrictlyIsolatedByStudentAndKnowledge() {
        String firstStudent = student(), secondStudent = student(), subject = subject();
        KnowledgeNodeReferenceEntity firstNode = node(subject), secondNode = node(subject);
        createExam(firstStudent, subject, firstNode, "90", "100", LocalDate.of(2026, 1, 1));
        createExam(secondStudent, subject, firstNode, "50", "100", LocalDate.of(2026, 1, 1));
        createExam(firstStudent, subject, secondNode, "60", "100", LocalDate.of(2026, 1, 1));
        assertThat(current(firstStudent, firstNode).getMasteryScore()).isEqualByComparingTo("60.00");
        assertThat(current(secondStudent, firstNode).getMasteryScore()).isEqualByComparingTo("30.00");
        assertThat(current(firstStudent, secondNode).getMasteryScore()).isEqualByComparingTo("45.00");
    }

    @Test void automaticHistoryIsImmutableMathematicallyConsistentAndAuditable() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        createExam(student, subject, node, "90", "100", LocalDate.of(2026, 1, 1));
        createExam(student, subject, node, "90", "100", LocalDate.of(2026, 1, 2));
        List<MasteryHistoryEntity> rows = histories(student, node);
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getScoreAfter().subtract(row.getScoreBefore())).isEqualByComparingTo(row.getChangeValue());
            assertThat(row.getManualFlag()).isFalse();
            assertThat(row.getDifficultyFactor()).isNull();
            assertThat(row.getConfidence()).isNull();
            assertThat(row.getKnowledgeWeight()).isNull();
            assertThat(row.getStreakAdjustment()).isNull();
            assertThat(row.getCalculationDetailJson()).contains("\"algorithmVersion\": \"1.0\"");
        });
    }

    @Test void manualAdjustmentCreatesLockedMasteryWithRealEvidenceStatistics() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        createExam(student, subject, node, "90", "100", LocalDate.of(2026, 1, 1));
        StudentMasteryEntity before = current(student, node);
        MasteryDto adjusted = mastery.adjust(node.getId().toString(), adjust(student, "77", before.getVersion(), true));
        StudentMasteryEntity stored = current(student, node);
        assertThat(adjusted.getScore()).isEqualByComparingTo("77.00");
        assertThat(stored.getManualLocked()).isTrue();
        assertThat(stored.getEvidenceCount()).isOne();
        assertThat(histories(student, node).getFirst().getEventType()).isEqualTo("MANUAL_ADJUST");
        assertThat(histories(student, node).getFirst().getManualFlag()).isTrue();
    }

    @Test void manualAdjustmentCanCreateWithoutEvidenceButDoesNotInventEvidence() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        MasteryDto adjusted = mastery.adjust(node.getId().toString(), adjust(student, "72.345", 0, true));
        StudentMasteryEntity stored = current(student, node);
        assertThat(adjusted.getScore()).isEqualByComparingTo("72.35");
        assertThat(stored.getEvidenceCount()).isZero();
        MasteryHistoryEntity history = histories(student, node).getFirst();
        assertThat(history.getScoreBefore()).isEqualByComparingTo("72.35");
        assertThat(history.getChangeValue()).isEqualByComparingTo("0.00");
    }

    @Test void manualAdjustmentRejectsFalseLockAndStaleVersion() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        assertThatThrownBy(() -> mastery.adjust(node.getId().toString(), adjust(student, "70", 0, false)))
                .isInstanceOf(BusinessException.class).satisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo("VALIDATION_ERROR"));
        MasteryDto created = mastery.adjust(node.getId().toString(), adjust(student, "70", 0, true));
        mastery.adjust(node.getId().toString(), adjust(student, "75", created.getVersion(), true));
        assertThatThrownBy(() -> mastery.adjust(node.getId().toString(), adjust(student, "80", 0, true)))
                .isInstanceOf(BusinessException.class).satisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo("DATA_VERSION_CONFLICT"));
        assertThat(created.getVersion()).isZero();
    }

    @Test void lockedAutomaticReplayPreservesScoreButUpdatesEvidenceCountsAndSchedule() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        createExam(student, subject, node, "90", "100", LocalDate.of(2026, 1, 1));
        StudentMasteryEntity before = current(student, node);
        mastery.adjust(node.getId().toString(), adjust(student, "77", before.getVersion(), true));
        Wrong question = createWrong(student, subject, List.of(link(node)));
        wrongQuestions.review(question.getId(), key(), review(ReviewResult.CORRECT, 1));
        StudentMasteryEntity stored = current(student, node);
        assertThat(stored.getMasteryScore()).isEqualByComparingTo("77.00");
        assertThat(stored.getEvidenceCount()).isEqualTo(3);
        assertThat(stored.getCorrectCount()).isEqualTo(2);
        assertThat(stored.getWrongCount()).isOne();
        assertThat(stored.getNextReviewTime()).isNotNull();
        assertThat(stored.getManualLocked()).isTrue();
    }

    @Test void unlockReplaysEvidenceAndAlwaysWritesManualHistory() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        createExam(student, subject, node, "90", "100", LocalDate.of(2026, 1, 1));
        StudentMasteryEntity before = current(student, node);
        mastery.adjust(node.getId().toString(), adjust(student, "60", before.getVersion(), true));
        int historyBefore = histories(student, node).size();
        MasteryDto unlocked = mastery.unlock(student, node.getId().toString());
        assertThat(unlocked.getLocked()).isFalse();
        assertThat(unlocked.getScore()).isEqualByComparingTo("60.00");
        assertThat(histories(student, node)).hasSize(historyBefore + 1);
        assertThat(histories(student, node).getFirst().getEventType()).isEqualTo("MANUAL_UNLOCK");
    }

    @Test void unlockWithoutEvidenceFailsAndCannotAffectAnotherStudent() {
        String first = student(), second = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        mastery.adjust(node.getId().toString(), adjust(first, "70", 0, true));
        assertThatThrownBy(() -> mastery.unlock(first, node.getId().toString())).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo("BUSINESS_RULE_VIOLATION"));
        assertThatThrownBy(() -> mastery.unlock(second, node.getId().toString()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(current(first, node).getManualLocked()).isTrue();
    }

    @Test void examUpdateRecalculatesOldAndNewKnowledgeUnion() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity oldNode = node(subject), newNode = node(subject);
        Exam exam = createExam(student, subject, oldNode, "90", "100", LocalDate.of(2026, 1, 1));
        ExamUpdate update = new ExamUpdate().studentId(student).examName("changed").examType(exam.getExamType())
                .examDate(exam.getExamDate()).version(exam.getVersion())
                .subjects(List.of(subjectScore(subject, newNode, "50", "100", 10, 5)));
        exams.update(exam.getId(), update);
        assertThat(find(student, oldNode)).isNull();
        assertThat(current(student, newNode).getMasteryScore()).isEqualByComparingTo("30.00");
    }

    @Test void examDeleteRemovesAutomaticMasteryWhenNoEvidenceRemains() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        Exam exam = createExam(student, subject, node, "90", "100", LocalDate.of(2026, 1, 1));
        exams.delete(exam.getId());
        assertThat(find(student, node)).isNull();
        assertThat(histories(student, node)).isNotEmpty();
    }

    @Test void wrongQuestionUpdateRecalculatesOldAndNewKnowledgeUnion() {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity oldNode = node(subject), newNode = node(subject);
        Wrong question = createWrong(student, subject, List.of(link(oldNode)));
        wrongQuestions.update(question.getId(), update(question, subject, List.of(link(newNode))));
        assertThat(find(student, oldNode)).isNull();
        assertThat(current(student, newNode).getMasteryScore()).isEqualByComparingTo("30.00");
    }

    @Test void masteryListAndHistoryFilterWithoutCrossStudentLeakage() {
        String first = student(), second = student(), subject = subject();
        KnowledgeNodeReferenceEntity weak = node(subject), strong = node(subject);
        createExam(first, subject, weak, "50", "100", LocalDate.of(2026, 1, 1));
        createExam(first, subject, strong, "90", "100", LocalDate.of(2026, 1, 1));
        createExam(second, subject, weak, "90", "100", LocalDate.of(2026, 1, 1));
        assertThat(mastery.list(first, subject, null, new BigDecimal("0"), new BigDecimal("40")))
                .extracting(MasteryDto::getKnowledgeId).containsExactly(weak.getId().toString());
        assertThat(mastery.history(first, weak.getId().toString(), 1, 20).getItems())
                .allSatisfy(item -> assertThat(item.getStudentId()).isEqualTo(first));
        assertThat(mastery.history(second, weak.getId().toString(), 1, 20).getItems())
                .allSatisfy(item -> assertThat(item.getStudentId()).isEqualTo(second));
    }

    @Test void masteryHttpApisReturnStringIdsRequestIdPaginationAndUnifiedErrors() throws Exception {
        String student = student(), subject = subject();
        KnowledgeNodeReferenceEntity node = node(subject);
        createExam(student, subject, node, "90", "100", LocalDate.of(2026, 1, 1));
        mvc.perform(get("/api/v1/knowledge/mastery").param("studentId", student)
                .header("X-Request-ID", "stage6-request"))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-ID", "stage6-request"))
                .andExpect(jsonPath("$.requestId").value("stage6-request"))
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].studentId").isString())
                .andExpect(jsonPath("$.data[0].knowledgeId").isString());
        mvc.perform(get("/api/v1/knowledge/mastery/{knowledgeId}/history", node.getId()).param("studentId", student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.items[0].id").isString());
        mvc.perform(post("/api/v1/knowledge/mastery/{knowledgeId}/adjust", node.getId())
                .contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.fieldErrors").isArray());
    }

    @Test void dueReviewBehaviorRemainsUnchangedAndUnlinkedQuestionsDoNotCreateMastery() {
        String student = student(), subject = subject();
        Wrong unlinked = createWrong(student, subject, List.of());
        assertThat(wrongQuestions.due(student, OffsetDateTime.now().plusMinutes(1), 1, 20).getItems())
                .extracting(Wrong::getId).contains(unlinked.getId());
        assertThat(masteryMapper.selectCount(Wrappers.<StudentMasteryEntity>lambdaQuery()
                .eq(StudentMasteryEntity::getStudentId, Long.valueOf(student)))).isZero();
    }

    private String student() {
        return students.create(new StudentCreate().name("stage6 " + UUID.randomUUID())
                .currentStageId("1").currentGradeId("1")).getId();
    }

    private String subject() {
        return subjects.selectList(null).stream().filter(SubjectEntity::getEnabled).findFirst().orElseThrow()
                .getId().toString();
    }

    private KnowledgeNodeReferenceEntity node(String subjectId) {
        KnowledgeNodeReferenceEntity node = new KnowledgeNodeReferenceEntity();
        node.setNodeCode("STAGE6-" + UUID.randomUUID());
        node.setName("Stage Six Knowledge");
        node.setNodeType("KNOWLEDGE_POINT");
        node.setSubjectId(Long.valueOf(subjectId));
        node.setEnabled(true);
        node.setDeleted(false);
        nodes.insert(node);
        return node;
    }

    private Exam createExam(String student, String subject, KnowledgeNodeReferenceEntity node, String score,
            String fullScore, LocalDate date) {
        int questions = 10;
        int correct = new BigDecimal(score).multiply(BigDecimal.TEN).divide(new BigDecimal(fullScore), 0,
                java.math.RoundingMode.DOWN).intValue();
        return createExam(student, subject, node, score, fullScore, questions, correct, date);
    }

    private Exam createExam(String student, String subject, KnowledgeNodeReferenceEntity node, String score,
            String fullScore, int questionCount, int correctCount, LocalDate date) {
        SubjectScore subjectScore = subjectScore(subject, node, score, fullScore, questionCount, correctCount);
        return exams.create(new ExamCreate().studentId(student).examName("exam " + UUID.randomUUID())
                .examType(ExamType.DAILY_TEST).examDate(date).subjects(List.of(subjectScore)));
    }

    private SubjectScore subjectScore(String subject, KnowledgeNodeReferenceEntity node, String score,
            String fullScore, int questionCount, int correctCount) {
        ScoreKnowledgeInput detail = new ScoreKnowledgeInput().knowledgeId(node.getId().toString())
                .score(new BigDecimal(score)).fullScore(new BigDecimal(fullScore)).questionCount(questionCount)
                .correctCount(correctCount);
        return new SubjectScore().subjectId(subject).score(new BigDecimal(score)).fullScore(new BigDecimal(fullScore))
                .knowledgeScores(List.of(detail));
    }

    private Wrong createWrong(String student, String subject, List<KnowledgeLink> links) {
        return wrongQuestions.create(new WrongCreate().studentId(student).subjectId(subject)
                .sourceType(WrongSource.PRACTICE).questionText("question " + UUID.randomUUID())
                .knowledgePoints(links));
    }

    private KnowledgeLink link(KnowledgeNodeReferenceEntity node) {
        return new KnowledgeLink().knowledgeId(node.getId().toString()).primary(false);
    }

    private WrongUpdate update(Wrong wrong, String subject, List<KnowledgeLink> links) {
        return new WrongUpdate().studentId(wrong.getStudentId()).subjectId(subject).sourceType(wrong.getSourceType())
                .questionText(wrong.getQuestionText()).studentAnswer(wrong.getStudentAnswer())
                .correctAnswer(wrong.getCorrectAnswer()).analysisText(wrong.getAnalysisText())
                .errorType(wrong.getErrorType()).difficulty(wrong.getDifficulty()).knowledgePoints(links)
                .version(wrong.getVersion());
    }

    private ReviewCreate review(ReviewResult result, int hour) {
        return new ReviewCreate().reviewTime(OffsetDateTime.now().withHour(Math.min(hour, 23))).result(result)
                .durationSeconds(30);
    }

    private MasteryAdjustRequest adjust(String student, String score, int version, boolean lock) {
        return new MasteryAdjustRequest().studentId(student).targetScore(new BigDecimal(score)).lockResult(lock)
                .reason("manual correction").version(version);
    }

    private StudentMasteryEntity current(String student, KnowledgeNodeReferenceEntity node) {
        StudentMasteryEntity value = find(student, node);
        assertThat(value).isNotNull();
        return value;
    }

    private StudentMasteryEntity find(String student, KnowledgeNodeReferenceEntity node) {
        return masteryMapper.selectOne(Wrappers.<StudentMasteryEntity>lambdaQuery()
                .eq(StudentMasteryEntity::getStudentId, Long.valueOf(student))
                .eq(StudentMasteryEntity::getKnowledgeId, node.getId()));
    }

    private List<MasteryHistoryEntity> histories(String student, KnowledgeNodeReferenceEntity node) {
        return historyMapper.selectList(Wrappers.<MasteryHistoryEntity>lambdaQuery()
                .eq(MasteryHistoryEntity::getStudentId, Long.valueOf(student))
                .eq(MasteryHistoryEntity::getKnowledgeId, node.getId())
                .orderByDesc(MasteryHistoryEntity::getCreateTime).orderByDesc(MasteryHistoryEntity::getId));
    }

    private void setNextReview(Wrong wrong, LocalDateTime next) {
        WrongQuestionEntity entity = wrongQuestionMapper.selectById(Long.valueOf(wrong.getId()));
        entity.setNextReviewTime(next);
        wrongQuestionMapper.updateById(entity);
    }

    private String key() { return "stage6-" + UUID.randomUUID(); }
}
