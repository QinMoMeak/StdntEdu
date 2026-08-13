package com.stdntedu.stage4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.stdntedu.base.entity.SubjectEntity;
import com.stdntedu.base.mapper.SubjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.generated.model.AcademicTermCreateRequest;
import com.stdntedu.generated.model.AcademicTermDto;
import com.stdntedu.generated.model.Exam;
import com.stdntedu.generated.model.ExamCreate;
import com.stdntedu.generated.model.ExamType;
import com.stdntedu.generated.model.ExamUpdate;
import com.stdntedu.generated.model.ScoreKnowledgeInput;
import com.stdntedu.generated.model.ScoreListItemDto;
import com.stdntedu.generated.model.ScoreTrendPointDto;
import com.stdntedu.generated.model.SemesterType;
import com.stdntedu.generated.model.Student;
import com.stdntedu.generated.model.StudentCreate;
import com.stdntedu.generated.model.SubjectScore;
import com.stdntedu.score.entity.ExamEntity;
import com.stdntedu.score.entity.KnowledgeNodeReferenceEntity;
import com.stdntedu.score.mapper.ExamMapper;
import com.stdntedu.score.mapper.KnowledgeNodeReferenceMapper;
import com.stdntedu.score.service.ExamService;
import com.stdntedu.student.service.AcademicTermService;
import com.stdntedu.student.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class StageFourIntegrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.36").asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("student_growth").withUsername("student_growth").withPassword("student_growth");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private StudentService students;
    @Autowired private AcademicTermService terms;
    @Autowired private ExamService exams;
    @Autowired private SubjectMapper subjects;
    @Autowired private KnowledgeNodeReferenceMapper knowledgeNodes;
    @Autowired private ExamMapper examMapper;
    @Autowired private MockMvc mockMvc;

    @Test
    void createsSingleAndMultiSubjectExamsAndCalculatesTotals() {
        Student student = student("Create Totals");
        Exam single = exams.create(exam(student.getId(), null, "single", LocalDate.of(2026, 5, 1), ExamType.DAILY_TEST,
                List.of(score(subjectId(0), "80", "100"))));
        assertThat(single.getSubjects()).hasSize(1);
        assertThat(single.getTotalScore()).isEqualByComparingTo("80");
        assertThat(single.getTotalFullScore()).isEqualByComparingTo("100");
        assertThat(single.getTotalScoreRate()).isEqualByComparingTo("0.8000");

        Exam multi = exams.create(exam(student.getId(), null, "multi", LocalDate.of(2026, 5, 2), ExamType.UNIT_TEST,
                List.of(score(subjectId(0), "75", "100"), score(subjectId(1), "45", "60"))));
        assertThat(multi.getSubjects()).hasSize(2);
        assertThat(multi.getTotalScore()).isEqualByComparingTo("120");
        assertThat(multi.getTotalFullScore()).isEqualByComparingTo("160");
        assertThat(multi.getTotalScoreRate()).isEqualByComparingTo("0.7500");
    }

    @Test
    void rejectsInvalidExamAndSubjectScoreInputs() {
        Student student = student("Invalid Inputs");
        Student another = student("Term Owner");
        AcademicTermDto foreignTerm = term(another.getId(), "2026-2027");
        assertThatThrownBy(() -> exams.create(exam("999999", null, "missing", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(subjectId(0), "1", "1"))))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> exams.create(exam(student.getId(), foreignTerm.getId(), "term", LocalDate.now(),
                ExamType.DAILY_TEST, List.of(score(subjectId(0), "1", "1"))))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> exams.create(exam(student.getId(), null, "subject", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score("999999", "1", "2"))))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> exams.create(exam(student.getId(), null, "duplicate", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(subjectId(0), "1", "2"), score(subjectId(0), "1", "2"))))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> exams.create(exam(student.getId(), null, "score", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(subjectId(0), "3", "2"))))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> exams.create(exam(student.getId(), null, "rank", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(subjectId(0), "1", "2").classRank(1))))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> exams.create(exam(student.getId(), null, "rank-size", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(subjectId(0), "1", "2").classRank(3).classSize(2))))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> exams.create(exam(student.getId(), null, "grade-rank", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(subjectId(0), "1", "2").gradeRank(3).gradeSize(2))))).isInstanceOf(BusinessException.class);
    }

    @Test
    void savesKnowledgeScoresAndRejectsInvalidKnowledgeInputs() {
        Student student = student("Knowledge Scores");
        String firstSubject = subjectId(0);
        String secondSubject = subjectId(1);
        KnowledgeNodeReferenceEntity node = knowledge(firstSubject);
        SubjectScore valid = score(firstSubject, "80", "100").knowledgeScores(List.of(knowledgeScore(node.getId().toString(), "8", "10", 10, 8)));
        Exam created = exams.create(exam(student.getId(), null, "knowledge", LocalDate.now(), ExamType.DAILY_TEST, List.of(valid)));
        assertThat(created.getSubjects().getFirst().getKnowledgeScores()).hasSize(1);
        assertThat(created.getSubjects().getFirst().getKnowledgeScores().getFirst().getScoreRate()).isEqualByComparingTo("0.8000");
        assertThat(created.getSubjects().getFirst().getKnowledgeScores().getFirst().getCorrectRate()).isEqualByComparingTo("0.8000");

        assertThatThrownBy(() -> exams.create(exam(student.getId(), null, "unknown knowledge", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(firstSubject, "1", "2").knowledgeScores(List.of(knowledgeScore("999999", "1", "2", 2, 1))))))).isInstanceOf(BusinessException.class);
        KnowledgeNodeReferenceEntity otherSubjectNode = knowledge(secondSubject);
        assertThatThrownBy(() -> exams.create(exam(student.getId(), null, "wrong subject", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(firstSubject, "1", "2").knowledgeScores(List.of(knowledgeScore(otherSubjectNode.getId().toString(), "1", "2", 2, 1))))))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> exams.create(exam(student.getId(), null, "duplicate knowledge", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(firstSubject, "1", "2").knowledgeScores(List.of(knowledgeScore(node.getId().toString(), "1", "2", 2, 1), knowledgeScore(node.getId().toString(), "1", "2", 2, 1))))))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> exams.create(exam(student.getId(), null, "knowledge score", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(firstSubject, "1", "2").knowledgeScores(List.of(knowledgeScore(node.getId().toString(), "3", "2", 2, 1))))))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> exams.create(exam(student.getId(), null, "correct count", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(firstSubject, "1", "2").knowledgeScores(List.of(knowledgeScore(node.getId().toString(), "1", "2", 2, 3))))))).isInstanceOf(BusinessException.class);
    }

    @Test
    void getsCompleteExamWithStringIdsAndPersistedTotals() {
        Student student = student("Get Exam");
        Exam created = exams.create(exam(student.getId(), null, "detail", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(subjectId(0), "17", "20"))));
        Exam detail = exams.get(created.getId());
        ExamEntity stored = examMapper.selectById(Long.valueOf(created.getId()));
        assertThat(detail.getId()).matches("[0-9]+");
        assertThat(detail.getStudentId()).matches("[0-9]+");
        assertThat(detail.getSubjects().getFirst().getSubjectId()).matches("[0-9]+");
        assertThat(detail.getTotalScore()).isEqualByComparingTo(stored.getTotalScore());
        assertThat(detail.getTotalFullScore()).isEqualByComparingTo(stored.getTotalFullScore());
        assertThatThrownBy(() -> exams.get("999999")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void replacesScoreCollectionAndUsesOptimisticExamVersion() {
        Student student = student("Update Exam");
        KnowledgeNodeReferenceEntity node = knowledge(subjectId(0));
        Exam created = exams.create(exam(student.getId(), null, "before", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(subjectId(0), "40", "50").knowledgeScores(List.of(knowledgeScore(node.getId().toString(), "4", "5", 5, 4))))));
        ExamUpdate foreignTermUpdate = update(created, "foreign term", List.of(score(subjectId(0), "40", "50")));
        foreignTermUpdate.academicTermId(term(student("Other Term Owner").getId(), "2027-2028").getId());
        assertThatThrownBy(() -> exams.update(created.getId(), foreignTermUpdate)).isInstanceOf(BusinessException.class);

        ExamUpdate firstUpdate = update(created, "after", List.of(
                score(subjectId(0), "50", "50").knowledgeScores(List.of(knowledgeScore(node.getId().toString(), "5", "5", 5, 5))),
                score(subjectId(1), "30", "40")));
        Exam updated = exams.update(created.getId(), firstUpdate);
        assertThat(updated.getExamName()).isEqualTo("after");
        assertThat(updated.getSubjects()).hasSize(2);
        assertThat(updated.getTotalScore()).isEqualByComparingTo("80");
        assertThat(updated.getVersion()).isEqualTo(created.getVersion() + 1);
        assertThat(updated.getSubjects().getFirst().getKnowledgeScores().getFirst().getCorrectRate()).isEqualByComparingTo("1.0000");

        ExamUpdate removeSubject = update(updated, "after removal", List.of(score(subjectId(1), "35", "40")));
        Exam removed = exams.update(updated.getId(), removeSubject);
        assertThat(removed.getSubjects()).hasSize(1);
        assertThat(removed.getSubjects().getFirst().getSubjectId()).isEqualTo(subjectId(1));
        assertThatThrownBy(() -> exams.update(created.getId(), firstUpdate)).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo("DATA_VERSION_CONFLICT"));
    }

    @Test
    void logicallyDeletesExamAndHidesItsScores() {
        Student student = student("Delete Exam");
        Exam created = exams.create(exam(student.getId(), null, "delete", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(subjectId(0), "1", "2"))));
        exams.delete(created.getId());
        assertThatThrownBy(() -> exams.get(created.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(exams.listScores(student.getId(), null, null, null, null, null, null, 1, 20).getItems()).isEmpty();
        assertThatThrownBy(() -> exams.delete(created.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void filtersScoreListsAndReturnsCalculatedRates() {
        Student student = student("Score Filters");
        AcademicTermDto firstTerm = term(student.getId(), "2025-2026");
        AcademicTermDto secondTerm = term(student.getId(), "2026-2027");
        exams.create(exam(student.getId(), firstTerm.getId(), "Alpha daily", LocalDate.of(2026, 1, 2), ExamType.DAILY_TEST,
                List.of(score(subjectId(0), "1", "3"))));
        exams.create(exam(student.getId(), secondTerm.getId(), "Beta unit", LocalDate.of(2026, 2, 2), ExamType.UNIT_TEST,
                List.of(score(subjectId(1), "3", "4"))));
        var bySubject = exams.listScores(student.getId(), null, subjectId(0), null, null, null, null, 1, 20);
        assertThat(bySubject.getItems()).hasSize(1).allMatch(item -> item.getSubjectId().equals(subjectId(0)));
        assertThat(exams.listScores(student.getId(), firstTerm.getId(), null, null, null, null, null, 1, 20).getItems()).hasSize(1);
        assertThat(exams.listScores(student.getId(), null, null, ExamType.UNIT_TEST, null, null, null, 1, 20).getItems()).hasSize(1);
        assertThat(exams.listScores(student.getId(), null, null, null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 3), null, 1, 20).getItems()).hasSize(1);
        assertThatThrownBy(() -> exams.listScores(student.getId(), null, null, null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1), null, 1, 20))
                .isInstanceOf(BusinessException.class);
        assertThat(exams.listScores(student.getId(), null, null, null, null, null, null, 1, 1).getItems()).hasSize(1);
        ScoreListItemDto item = bySubject.getItems().getFirst();
        assertThat(item.getScoreRate()).isEqualByComparingTo("0.3333");
    }

    @Test
    void returnsSubjectAndOverallTrendsInAscendingDateOrder() {
        Student student = student("Trends");
        exams.create(exam(student.getId(), null, "later", LocalDate.of(2026, 6, 2), ExamType.DAILY_TEST,
                List.of(score(subjectId(0), "1", "3"), score(subjectId(1), "5", "6"))));
        exams.create(exam(student.getId(), null, "earlier", LocalDate.of(2026, 6, 1), ExamType.DAILY_TEST,
                List.of(score(subjectId(0), "2", "3"), score(subjectId(1), "4", "6"))));
        List<ScoreTrendPointDto> subjectTrend = exams.trends(student.getId(), subjectId(0), null, null, null);
        assertThat(subjectTrend).hasSize(2);
        assertThat(subjectTrend).extracting(ScoreTrendPointDto::getExamDate).isSorted();
        assertThat(subjectTrend.getFirst().getScoreRate()).isEqualByComparingTo("0.6667");
        List<ScoreTrendPointDto> totalTrend = exams.trends(student.getId(), null, null, null, null);
        assertThat(totalTrend).hasSize(2);
        assertThat(totalTrend.getFirst().getScoreRate()).isEqualByComparingTo("0.6667");
    }

    @Test
    void exposesUnifiedHttpErrorsRequestIdsAndStringScoreIds() throws Exception {
        Student student = student("HTTP Score");
        Exam created = exams.create(exam(student.getId(), null, "http", LocalDate.now(), ExamType.DAILY_TEST,
                List.of(score(subjectId(0), "1", "2"))));
        mockMvc.perform(get("/api/v1/exams/{examId}", created.getId()).header("X-Request-ID", "stage4-http"))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-ID", "stage4-http"))
                .andExpect(jsonPath("$.requestId").value("stage4-http")).andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.studentId").isString()).andExpect(jsonPath("$.data.subjects[0].subjectId").isString());
        mockMvc.perform(get("/api/v1/exams/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(post("/api/v1/exams").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.fieldErrors").isArray());
    }

    private Student student(String name) {
        return students.create(new StudentCreate().name(name + " " + UUID.randomUUID()).currentStageId("1").currentGradeId("1"));
    }

    private AcademicTermDto term(String studentId, String year) {
        return terms.create(new AcademicTermCreateRequest().studentId(studentId).academicYear(year).semester(SemesterType.FIRST)
                .stageId("1").gradeId("1").startDate(LocalDate.of(Integer.parseInt(year.substring(0, 4)), 9, 1))
                .endDate(LocalDate.of(Integer.parseInt(year.substring(5)), 1, 31)).current(false));
    }

    private String subjectId(int index) {
        return subjects.selectList(null).stream().filter(SubjectEntity::getEnabled).skip(index).findFirst().orElseThrow().getId().toString();
    }

    private KnowledgeNodeReferenceEntity knowledge(String subjectId) {
        KnowledgeNodeReferenceEntity node = new KnowledgeNodeReferenceEntity();
        node.setNodeCode("STAGE4-" + UUID.randomUUID());
        node.setName("Stage Four Knowledge");
        node.setNodeType("KNOWLEDGE_POINT");
        node.setSubjectId(Long.valueOf(subjectId));
        node.setEnabled(true);
        node.setDeleted(false);
        knowledgeNodes.insert(node);
        return node;
    }

    private ExamCreate exam(String studentId, String termId, String name, LocalDate date, ExamType type, List<SubjectScore> scores) {
        return new ExamCreate().studentId(studentId).academicTermId(termId).examName(name).examType(type).examDate(date).subjects(scores);
    }

    private ExamUpdate update(Exam exam, String name, List<SubjectScore> scores) {
        return new ExamUpdate().studentId(exam.getStudentId()).academicTermId(exam.getAcademicTermId()).examName(name)
                .examType(exam.getExamType()).examDate(exam.getExamDate()).subjects(scores).version(exam.getVersion());
    }

    private SubjectScore score(String subjectId, String score, String fullScore) {
        return new SubjectScore().subjectId(subjectId).score(new BigDecimal(score)).fullScore(new BigDecimal(fullScore));
    }

    private ScoreKnowledgeInput knowledgeScore(String knowledgeId, String score, String fullScore, int questionCount, int correctCount) {
        return new ScoreKnowledgeInput().knowledgeId(knowledgeId).score(new BigDecimal(score)).fullScore(new BigDecimal(fullScore))
                .questionCount(questionCount).correctCount(correctCount);
    }
}
