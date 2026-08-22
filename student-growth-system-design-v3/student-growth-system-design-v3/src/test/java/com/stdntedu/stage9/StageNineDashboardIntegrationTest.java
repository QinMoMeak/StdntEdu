package com.stdntedu.stage9;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.dashboard.service.DashboardService;
import com.stdntedu.generated.model.DashboardDto;
import com.stdntedu.generated.model.ResourceStatus;
import com.stdntedu.generated.model.StudentResourceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
class StageNineDashboardIntegrationTest {
    private static final LocalDate TARGET = LocalDate.of(2026, 8, 10);
    private static final List<String> READ_ONLY_TABLES = List.of(
            "exam", "wrong_question", "wrong_review", "student_mastery", "mastery_history",
            "learning_resource", "student_resource_assignment", "resource_history", "study_log",
            "study_plan", "study_plan_task", "study_plan_action_history");

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

    @Autowired DashboardService dashboards;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test void scenarios01_08_basicValidationTimezoneAndDefaultDate() {
        assertThatThrownBy(() -> dashboards.get("999999999", null, TARGET, null))
                .isInstanceOf(ResourceNotFoundException.class);
        Long student = student();
        DashboardDto normal = dashboards.get(student.toString(), null, TARGET, null);
        assertThat(normal.getStatisticsPeriod().getStartDate()).isEqualTo(TARGET.minusDays(29));
        assertThat(normal.getStatisticsPeriod().getEndDate()).isEqualTo(TARGET);
        assertThat(normal.getAiSuggestions()).isEmpty();
        assertThat(dashboards.get(student.toString(), null, TARGET, "UTC").getToday()).isNotNull();
        assertThatThrownBy(() -> dashboards.get(student.toString(), null, TARGET, "Not/A_Zone"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo("BUSINESS_RULE_VIOLATION"));
        LocalDate configuredToday = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        DashboardDto current = dashboards.get(student.toString(), null, null, null);
        assertThat(current.getStatisticsPeriod().getEndDate()).isEqualTo(configuredToday);
    }

    @Test void scenarios09_14_statisticsPeriodUsesExactFrozenWindowAndTermBounds() {
        Long student = student();
        DashboardDto rolling = dashboards.get(student.toString(), null, TARGET, null);
        assertThat(rolling.getStatisticsPeriod().getStartDate()).isEqualTo(TARGET.minusDays(29));
        Long term = term(student, TARGET.minusDays(20), TARGET.plusDays(20));
        DashboardDto inside = dashboards.get(student.toString(), term.toString(), TARGET, null);
        assertThat(inside.getStatisticsPeriod().getStartDate()).isEqualTo(TARGET.minusDays(20));
        assertThat(inside.getStatisticsPeriod().getEndDate()).isEqualTo(TARGET);
        assertThat(inside.getStatisticsPeriod().getAcademicTermId()).isEqualTo(term.toString());
        DashboardDto afterEnd = dashboards.get(student.toString(), term.toString(), TARGET.plusDays(30), null);
        assertThat(afterEnd.getStatisticsPeriod().getEndDate()).isEqualTo(TARGET.plusDays(20));
        assertThatThrownBy(() -> dashboards.get(student.toString(), term.toString(), TARGET.minusDays(21), null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> dashboards.get(student.toString(), "999999999", TARGET, null))
                .isInstanceOf(ResourceNotFoundException.class);
        Long other = student();
        assertThatThrownBy(() -> dashboards.get(other.toString(), term.toString(), TARGET, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test void scenarios15_19_studyDurationUsesOnlyTargetDayNonDeletedStudyLogs() {
        Long student = student();
        studyLog(student, TARGET, 600, false, null, "today one", LocalDateTime.of(2026, 8, 10, 8, 0));
        studyLog(student, TARGET, 900, false, null, "today two", LocalDateTime.of(2026, 8, 10, 9, 0));
        studyLog(student, TARGET.minusDays(1), 700, false, null, "yesterday", LocalDateTime.of(2026, 8, 9, 9, 0));
        studyLog(student, TARGET, 800, true, null, "deleted", LocalDateTime.of(2026, 8, 10, 10, 0));
        Long resource = resource("WAITING", false);
        jdbc.update("INSERT INTO resource_history(student_id,resource_id,duration_seconds,progress_percent,completed) VALUES (?,?,?,?,0)",
                student, resource, 5000, BigDecimal.TEN);
        assertThat(dashboards.get(student.toString(), null, TARGET, null).getToday().getStudyDurationSeconds())
                .isEqualTo(1500);
        assertThat(dashboards.get(student.toString(), null, TARGET.plusDays(10), null).getToday()
                .getStudyDurationSeconds()).isZero();
    }

    @Test void scenarios20_28_taskCountsUseActivePlanDateAndTaskRows() {
        Long student = student();
        Long resource = resource("WAITING", false);
        Long active = plan(student, "ACTIVE");
        task(active, TARGET, "TODO", resource);
        task(active, TARGET, "IN_PROGRESS", resource);
        task(active, TARGET, "COMPLETED", resource);
        task(active, TARGET, "CANCELLED", resource);
        task(active, TARGET.plusDays(1), "TODO", resource);
        task(plan(student, "DRAFT"), TARGET, "TODO", null);
        task(plan(student, "PAUSED"), TARGET, "TODO", null);
        task(plan(student, "COMPLETED"), TARGET, "TODO", null);
        DashboardDto result = dashboards.get(student.toString(), null, TARGET, null);
        assertThat(result.getToday().getTotalTaskCount()).isEqualTo(3);
        assertThat(result.getToday().getCompletedTaskCount()).isEqualTo(1);
    }

    @Test void scenarios29_35_and60_65_dueCountsAndListShareStrictBoundaries() {
        Long student = student();
        Long subject = subject();
        Long yesterday = wrong(student, subject, "NEW", false, TARGET.minusDays(1).atTime(12, 0));
        Long today = wrong(student, subject, "REVIEWING", false, TARGET.atTime(12, 0));
        wrong(student, subject, "NEW", false, TARGET.plusDays(1).atStartOfDay());
        wrong(student, subject, "ARCHIVED", false, TARGET.minusDays(1).atTime(10, 0));
        wrong(student, subject, "NEW", true, TARGET.minusDays(1).atTime(9, 0));
        wrong(student, subject, "NEW", false, null);
        Long other = student();
        wrong(other, subject, "NEW", false, TARGET.minusDays(2).atTime(9, 0));
        for (int index = 0; index < 5; index++) {
            wrong(student, subject, "NEW", false, TARGET.minusDays(5L - index).atTime(8, 0));
        }
        DashboardDto result = dashboards.get(student.toString(), null, TARGET, null);
        assertThat(result.getToday().getDueReviewCount()).isEqualTo(7);
        assertThat(result.getToday().getOverdueReviewCount()).isEqualTo(6);
        assertThat(result.getToday().getDueReviewCount()).isGreaterThanOrEqualTo(result.getToday().getOverdueReviewCount());
        assertThat(result.getDueReviews()).hasSize(5).isSortedAccordingTo((left, right) ->
                left.getNextReviewTime().compareTo(right.getNextReviewTime()));
        assertThat(result.getDueReviews()).extracting(item -> item.getId())
                .doesNotContain(today.toString()).doesNotContain(yesterday.toString());
    }

    @Test void scenarios36_49_latestExamAndTrendsUsePersistedTotalsStableOrderAndRecentTen() {
        Long student = student();
        assertThat(dashboards.get(student.toString(), null, TARGET, null).getLatestExam()).isNull();
        Long term = term(student, TARGET.minusDays(40), TARGET.plusDays(10));
        Long otherTerm = term(student, TARGET.minusDays(100), TARGET.minusDays(50));
        Long old = exam(student, term, TARGET.minusDays(20), "old", "50.00", "100.00",
                LocalDateTime.of(2026, 7, 21, 8, 0));
        for (int index = 0; index < 12; index++) {
            exam(student, term, TARGET.minusDays(11L - index), "trend-" + index,
                    Integer.toString(60 + index), "100.00", LocalDateTime.of(2026, 8, 1, 8, index));
        }
        Long sameDateOlder = exam(student, term, TARGET, "same-older", "70.00", "100.00",
                LocalDateTime.of(2026, 8, 10, 10, 0));
        Long sameDateNewer = exam(student, term, TARGET, "same-newer", "80.00", "100.00",
                LocalDateTime.of(2026, 8, 10, 11, 0));
        Long sameDateNewest = exam(student, term, TARGET, "same-newest", "85.00", "100.00",
                LocalDateTime.of(2026, 8, 10, 11, 0));
        exam(student, otherTerm, TARGET.minusDays(1), "other-term", "90.00", "100.00",
                LocalDateTime.of(2026, 8, 9, 8, 0));
        DashboardDto all = dashboards.get(student.toString(), null, TARGET, null);
        assertThat(all.getLatestExam().getId()).isEqualTo(sameDateNewest.toString());
        assertThat(all.getLatestExam().getTotalScore()).isEqualByComparingTo("85.00");
        assertThat(all.getLatestExam().getTotalFullScore()).isEqualByComparingTo("100.00");
        assertThat(all.getLatestExam().getScoreRate()).isEqualByComparingTo("0.8500");
        assertThat(all.getScoreTrends()).hasSize(10);
        assertThat(all.getScoreTrends()).extracting(point -> point.getExamDate()).isSorted();
        assertThat(all.getScoreTrends()).extracting(point -> point.getExamId())
                .contains(sameDateOlder.toString(), sameDateNewer.toString(), sameDateNewest.toString())
                .doesNotContain(old.toString());
        DashboardDto filtered = dashboards.get(student.toString(), term.toString(), TARGET, null);
        assertThat(filtered.getLatestExam().getId()).isEqualTo(sameDateNewest.toString());
        assertThat(filtered.getScoreTrends()).allSatisfy(point -> assertThat(point.getScoreRate()).isNotNull());
    }

    @Test void scenarios50_59_weakMasteryUsesRealRowsStableOrderAndNeverWritesHistory() {
        Long student = student();
        Long other = student();
        Long subject = subject();
        for (int index = 0; index < 7; index++) {
            Long knowledge = knowledge(subject, "knowledge-" + index);
            mastery(student, knowledge, index < 2 ? "20.00" : Integer.toString(30 + index),
                    index < 2 ? 10 - index : index, index == 0, TARGET.plusDays(index).atTime(9, 0),
                    LocalDateTime.of(2026, 8, 1, 8, index));
        }
        Long otherKnowledge = knowledge(subject, "other-knowledge");
        mastery(other, otherKnowledge, "1.00", 99, false, null, LocalDateTime.of(2026, 8, 1, 7, 0));
        int masteryBefore = count("student_mastery");
        int historyBefore = count("mastery_history");
        DashboardDto result = dashboards.get(student.toString(), null, TARGET, null);
        assertThat(result.getWeakKnowledge()).hasSize(5);
        assertThat(result.getWeakKnowledge().getFirst().getMasteryScore()).isEqualByComparingTo("20.00");
        assertThat(result.getWeakKnowledge().getFirst().getEvidenceCount()).isEqualTo(10);
        assertThat(result.getWeakKnowledge().getFirst().getNextReviewTime()).isNotNull();
        assertThat(result.getWeakKnowledge()).allSatisfy(item -> {
            assertThat(item.getKnowledgeId()).matches("[0-9]+");
            assertThat(item.getSubjectId()).isEqualTo(subject.toString());
        });
        assertThat(count("student_mastery")).isEqualTo(masteryBefore);
        assertThat(count("mastery_history")).isEqualTo(historyBefore);
        assertThat(dashboards.get(student().toString(), null, TARGET, null).getWeakKnowledge()).isEmpty();
    }

    @Test void deletedKnowledgeNodeIsExcludedFromWeakMasteryWithoutChangingMasteryRows() {
        Long student = student();
        Long subject = subject();
        Long activeKnowledge = knowledge(subject, "active-knowledge");
        Long deletedKnowledge = knowledge(subject, "deleted-knowledge");
        mastery(student, activeKnowledge, "20.00", 2, false, null, LocalDateTime.of(2026, 8, 1, 8, 0));
        mastery(student, deletedKnowledge, "1.00", 3, false, null, LocalDateTime.of(2026, 8, 1, 8, 1));
        jdbc.update("UPDATE knowledge_node SET deleted = 1 WHERE id = ?", deletedKnowledge);
        int masteryBefore = count("student_mastery");

        DashboardDto result = dashboards.get(student.toString(), null, TARGET, null);

        assertThat(result.getWeakKnowledge()).extracting(item -> item.getKnowledgeId())
                .contains(activeKnowledge.toString()).doesNotContain(deletedKnowledge.toString());
        assertThat(count("student_mastery")).isEqualTo(masteryBefore);
    }

    @Test void scenarios66_85_resourceCountsWaitingListAndLatestProgressAreAssignmentBased() {
        Long student = student();
        Long other = student();
        for (int index = 0; index < 7; index++) {
            Long resource = resource(index == 6 ? "LEARNING" : "WAITING", false);
            Long assignment = assignment(student, resource, index == 6 ? "LEARNING" : "WAITING",
                    LocalDateTime.of(2026, 8, 10, 8, index));
            if (index == 5) {
                history(student, resource, "0.00", LocalDateTime.of(2026, 8, 9, 8, 0));
            } else if (index == 4) {
                history(student, resource, "25.00", LocalDateTime.of(2026, 8, 9, 9, 0));
                history(student, resource, "75.00", LocalDateTime.of(2026, 8, 9, 9, 0));
                history(other, resource, "99.00", LocalDateTime.of(2026, 8, 10, 9, 0));
            }
            assertThat(assignment).isPositive();
        }
        assignment(student, resource("WAITING", false), "COMPLETED", LocalDateTime.now());
        assignment(student, resource("WAITING", false), "REVIEW", LocalDateTime.now());
        assignment(student, resource("WAITING", false), "ARCHIVED", LocalDateTime.now());
        assignment(student, resource("ARCHIVED", false), "WAITING", LocalDateTime.now());
        assignment(student, resource("WAITING", true), "WAITING", LocalDateTime.now());
        resource("WAITING", false);
        DashboardDto result = dashboards.get(student.toString(), null, TARGET, null);
        assertThat(result.getToday().getWaitingResourceCount()).isEqualTo(6);
        assertThat(result.getToday().getLearningResourceCount()).isEqualTo(1);
        assertThat(result.getWaitingResources()).hasSize(5);
        assertThat(result.getWaitingResources()).allSatisfy(item -> {
            assertThat(item.getStudentStatus()).isEqualTo(StudentResourceStatus.WAITING);
            assertThat(item.getStatus()).isNotEqualTo(ResourceStatus.ARCHIVED);
            assertThat(item.getId()).matches("[0-9]+");
            assertThat(item.getAssignmentId()).matches("[0-9]+");
        });
        assertThat(result.getWaitingResources()).extracting(item -> item.getAssignedTime()).isSortedAccordingTo(
                (left, right) -> right.compareTo(left));
        assertThat(result.getWaitingResources()).anySatisfy(item ->
                assertThat(item.getLatestProgressPercent()).isEqualByComparingTo("75.00"));
        assertThat(result.getWaitingResources()).anySatisfy(item ->
                assertThat(item.getLatestProgressPercent()).isEqualByComparingTo("0.00"));
        assertThat(result.getWaitingResources()).anySatisfy(item ->
                assertThat(item.getLatestProgressPercent()).isNull());
    }

    @Test void scenarios86_95_recentLogsUsePeriodStableLimitIsolationAndAiRemainsEmpty() {
        Long student = student();
        Long other = student();
        Long subject = subject();
        for (int index = 0; index < 7; index++) {
            studyLog(student, TARGET.minusDays(index), 100 + index, false, subject, "log-" + index,
                    LocalDateTime.of(2026, 8, 10, 12, index));
        }
        studyLog(student, TARGET.minusDays(40), 999, false, subject, "outside", LocalDateTime.now());
        studyLog(student, TARGET, 999, true, subject, "deleted", LocalDateTime.now());
        studyLog(other, TARGET, 999, false, subject, "other", LocalDateTime.now());
        DashboardDto result = dashboards.get(student.toString(), null, TARGET, null);
        assertThat(result.getRecentStudyLogs()).hasSize(5);
        assertThat(result.getRecentStudyLogs()).extracting(item -> item.getStudyDate()).isSortedAccordingTo(
                (left, right) -> right.compareTo(left));
        assertThat(result.getRecentStudyLogs()).allSatisfy(item -> {
            assertThat(item.getStudentId()).isEqualTo(student.toString());
            assertThat(item.getSubjectId()).isEqualTo(subject.toString());
            assertThat(item.getSubjectName()).isNotBlank();
        });
        assertThat(result.getRecentStudyLogs()).extracting(item -> item.getContent())
                .doesNotContain("outside", "deleted", "other");
        assertThat(result.getAiSuggestions()).isEmpty();
    }

    @Test void scenarios96_107_dashboardHttpCallIsProvablyReadOnlyAcrossAllAggregateTables() throws Exception {
        Long student = student();
        Long subject = subject();
        studyLog(student, TARGET, 60, false, subject, "snapshot", LocalDateTime.now());
        wrong(student, subject, "NEW", false, TARGET.atTime(8, 0));
        Long resource = resource("WAITING", false);
        assignment(student, resource, "WAITING", LocalDateTime.now());
        Long plan = plan(student, "ACTIVE");
        task(plan, TARGET, "TODO", resource);
        Map<String, Long> before = checksums();
        mvc.perform(get("/api/v1/dashboard").param("studentId", student.toString())
                .param("date", TARGET.toString()).header("X-Request-ID", "stage9-read-only"))
                .andExpect(status().isOk());
        assertThat(checksums()).isEqualTo(before);
    }

    @Test void scenarios108_111_httpErrorsRequestIdStringIdsAndNullsFollowFrozenEnvelope() throws Exception {
        mvc.perform(get("/api/v1/dashboard").param("studentId", "999999999"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        Long student = student();
        Long resource = resource("WAITING", false);
        assignment(student, resource, "WAITING", LocalDateTime.now());
        mvc.perform(get("/api/v1/dashboard").param("studentId", student.toString())
                .param("date", TARGET.toString()).header("X-Request-ID", "stage9-http"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "stage9-http"))
                .andExpect(jsonPath("$.requestId").value("stage9-http"))
                .andExpect(jsonPath("$.data.latestExam").doesNotExist())
                .andExpect(jsonPath("$.data.waitingResources[0].id").isString())
                .andExpect(jsonPath("$.data.waitingResources[0].assignmentId").isString())
                .andExpect(jsonPath("$.data.waitingResources[0].latestProgressPercent").doesNotExist());
        mvc.perform(get("/api/v1/dashboard").param("studentId", student.toString())
                .param("date", TARGET.toString()).param("timezone", "Invalid/Timezone"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    private Long student() {
        String code = "DASH-" + token();
        jdbc.update("INSERT INTO student(student_code,name,deleted,version) VALUES (?, 'Dashboard Student',0,0)", code);
        return lastId();
    }

    private Long subject() {
        return jdbc.queryForObject("SELECT id FROM subject WHERE enabled=1 ORDER BY id LIMIT 1", Long.class);
    }

    private Long term(Long student, LocalDate start, LocalDate end) {
        Long stage = jdbc.queryForObject("SELECT id FROM stage WHERE enabled=1 ORDER BY id LIMIT 1", Long.class);
        Long grade = jdbc.queryForObject("SELECT id FROM grade WHERE stage_id=? ORDER BY id LIMIT 1", Long.class, stage);
        String year = "D" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO academic_term(student_id,academic_year,semester,stage_id,grade_id,start_date,end_date,is_current,deleted,version) VALUES (?,?,?,?,?,?,?,0,0,0)",
                student, year, "FIRST", stage, grade, start, end);
        return lastId();
    }

    private Long exam(Long student, Long term, LocalDate date, String name, String score, String fullScore,
            LocalDateTime createTime) {
        jdbc.update("INSERT INTO exam(student_id,academic_term_id,exam_name,exam_type,exam_date,total_score,total_full_score,deleted,version,create_time,update_time) VALUES (?,?,?,?,?,?,?,0,0,?,?)",
                student, term, name, "OTHER", date, new BigDecimal(score), new BigDecimal(fullScore), createTime,
                createTime);
        return lastId();
    }

    private Long wrong(Long student, Long subject, String status, boolean deleted, LocalDateTime nextReviewTime) {
        jdbc.update("INSERT INTO wrong_question(student_id,subject_id,source_type,question_text,status,review_stage,review_count,next_review_time,deleted,version) VALUES (?,?, 'PRACTICE', ?,?,0,0,?,?,0)",
                student, subject, "question-" + UUID.randomUUID(), status, nextReviewTime, deleted);
        return lastId();
    }

    private Long knowledge(Long subject, String name) {
        jdbc.update("INSERT INTO knowledge_node(node_code,name,node_type,subject_id,enabled,deleted,version) VALUES (?,?, 'POINT',?,1,0,1)",
                "KD-" + token(), name, subject);
        return lastId();
    }

    private void mastery(Long student, Long knowledge, String score, int evidence, boolean locked,
            LocalDateTime nextReview, LocalDateTime updateTime) {
        jdbc.update("INSERT INTO student_mastery(student_id,knowledge_id,mastery_score,evidence_count,manual_locked,version,next_review_time,update_time) VALUES (?,?,?,?,?,0,?,?)",
                student, knowledge, new BigDecimal(score), evidence, locked, nextReview, updateTime);
    }

    private Long resource(String status, boolean deleted) {
        jdbc.update("INSERT INTO learning_resource(resource_code,title,resource_type,source_type,duration_seconds,status,deleted,version) VALUES (?,?, 'VIDEO','MANUAL',600,?,?,0)",
                "DR-" + token(), "resource-" + UUID.randomUUID(), status, deleted);
        return lastId();
    }

    private Long assignment(Long student, Long resource, String status, LocalDateTime assignedTime) {
        jdbc.update("INSERT INTO student_resource_assignment(student_id,resource_id,status,assigned_time,version) VALUES (?,?,?,?,0)",
                student, resource, status, assignedTime);
        return lastId();
    }

    private void history(Long student, Long resource, String progress, LocalDateTime createTime) {
        jdbc.update("INSERT INTO resource_history(student_id,resource_id,duration_seconds,progress_percent,completed,create_time) VALUES (?,?,60,?,0,?)",
                student, resource, new BigDecimal(progress), createTime);
    }

    private void studyLog(Long student, LocalDate date, int duration, boolean deleted, Long subject, String content,
            LocalDateTime createTime) {
        jdbc.update("INSERT INTO study_log(student_id,subject_id,study_date,duration_seconds,content,deleted,version,create_time,update_time) VALUES (?,?,?,?,?,?,0,?,?)",
                student, subject, date, duration, content, deleted, createTime, createTime);
    }

    private Long plan(Long student, String status) {
        jdbc.update("INSERT INTO study_plan(student_id,title,plan_type,start_date,end_date,status,deleted,version) VALUES (?,?,'MANUAL',?,?,?,0,1)",
                student, "plan-" + UUID.randomUUID(), TARGET.minusDays(10), TARGET.plusDays(10), status);
        return lastId();
    }

    private Long task(Long plan, LocalDate date, String status, Long resource) {
        jdbc.update("INSERT INTO study_plan_task(study_plan_id,task_date,task_type,title,resource_id,status,sort_order,version) VALUES (?,?,'OTHER',?,?,?,0,1)",
                plan, date, "task-" + UUID.randomUUID(), resource, status);
        return lastId();
    }

    private Long lastId() {
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private Map<String, Long> checksums() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String table : READ_ONLY_TABLES) {
            Long checksum = jdbc.queryForObject("CHECKSUM TABLE " + table,
                    (row, rowNumber) -> row.getLong("Checksum"));
            result.put(table, checksum);
        }
        return result;
    }
}
