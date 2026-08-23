package com.stdntedu.stage12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.common.web.GeneratedApiController;
import com.stdntedu.growth.report.service.GrowthReportDispatcher;
import com.stdntedu.growth.report.service.GrowthReportRecoveryService;
import com.stdntedu.growth.report.service.GrowthReportWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class StageTwelveFGrowthReportIntegrationTest {
    private static final List<String> USER_TABLES = List.of(
            "restore_record", "backup_record", "operation_log", "import_task", "export_task",
            "entity_attachment", "study_plan_action_history", "study_plan_task", "study_plan", "growth_report",
            "recommendation", "ai_extraction_confirmation_item", "ai_extraction_confirmation",
            "ai_extraction_correction", "ai_extraction_question_knowledge", "ai_extraction_question",
            "ai_extraction_file", "ai_extraction_task", "score_knowledge", "wrong_question_knowledge",
            "wrong_review", "student_mastery", "mastery_history", "resource_history",
            "student_resource_assignment", "learning_resource_knowledge", "score_record", "wrong_question",
            "exam", "study_log", "growth_event", "learning_resource", "ai_analysis", "ai_model", "ai_secret",
            "attachment", "knowledge_relation", "knowledge_node", "academic_term", "student");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.36").asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("student_growth").withUsername("student_growth").withPassword("student_growth");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.ai.extraction.pending-rescan.initial-delay-ms", () -> 3_600_000);
        registry.add("app.ai.study-plan.pending-rescan.initial-delay-ms", () -> 3_600_000);
        registry.add("app.transfer.pending-rescan.initial-delay-ms", () -> 3_600_000);
        registry.add("app.backup-restore.pending-rescan.initial-delay-ms", () -> 3_600_000);
        registry.add("app.growth-report.pending-rescan.initial-delay-ms", () -> 3_600_000);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired GrowthReportWorker worker;
    @Autowired GrowthReportRecoveryService recovery;
    @SpyBean GrowthReportDispatcher dispatcher;
    private long studentId;

    @BeforeEach
    void clean() {
        reset(dispatcher);
        doReturn(false).when(dispatcher).dispatch(anyLong());
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                for (String table : USER_TABLES) statement.executeUpdate("DELETE FROM `" + table + "`");
                statement.execute("SET FOREIGN_KEY_CHECKS=1");
            }
            return null;
        });
        jdbc.update("INSERT INTO student(student_code,name) VALUES('STAGE12F','阶段十二F学生')");
        studentId = jdbc.queryForObject("SELECT id FROM student WHERE student_code='STAGE12F'", Long.class);
    }

    @Test
    void scenarios01_20_contractCreateValidationAndCanonicalRequest() throws Exception {
        String reportId = createPending("MONTHLY", "2026-07-01", "2026-07-31", "七月成长报告");
        assertThat(reportId).matches("\\d+");
        assertThat(jdbc.queryForObject("SELECT generation_type FROM growth_report WHERE id=?", String.class,
                reportId)).isEqualTo("DETERMINISTIC");
        assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.studentId')) FROM growth_report WHERE id=?",
                String.class, reportId)).isEqualTo(Long.toString(studentId));
        verify(dispatcher).dispatch(Long.valueOf(reportId));

        mvc.perform(post("/api/v1/reports").contentType(MediaType.APPLICATION_JSON)
                        .content(request(999999, "DAILY", "2026-07-01", "2026-07-01", "missing")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/reports").contentType(MediaType.APPLICATION_JSON)
                        .content(request(studentId, "WEEKLY", "2026-07-01", "2026-07-20", "too long")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/reports").contentType(MediaType.APPLICATION_JSON)
                        .content(request(studentId, "DAILY", "2026-12-01", "2026-12-01", "future")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/reports").contentType(MediaType.APPLICATION_JSON)
                        .content(request(studentId, "DAILY", "2026-07-01", "2026-07-01", "ai")
                                .replace("}", ",\"modelId\":\"1\"}")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void scenarios21_36_generationUsesOneDeterministicSnapshotWithoutDomainWrites() throws Exception {
        seedReportData();
        java.math.BigDecimal masteryBefore = jdbc.queryForObject(
                "SELECT mastery_score FROM student_mastery WHERE student_id=?",
                java.math.BigDecimal.class, studentId);
        int plansBefore = count("study_plan");
        int eventsBefore = count("growth_event");
        String reportId = createPending("MONTHLY", "2026-07-01", "2026-07-31", "中文成长报告");

        worker.run(Long.valueOf(reportId));

        mvc.perform(get("/api/v1/reports/{id}", reportId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reportId))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.statisticsSnapshot.schemaVersion").value(1))
                .andExpect(jsonPath("$.data.statisticsSnapshot.scores.examCount").value(1))
                .andExpect(jsonPath("$.data.statisticsSnapshot.mastery.weakCount").value(1))
                .andExpect(jsonPath("$.data.statisticsSnapshot.wrongQuestions.totalCount").value(1))
                .andExpect(jsonPath("$.data.statisticsSnapshot.learning.studyLogCount").value(1))
                .andExpect(jsonPath("$.data.statisticsSnapshot.growthEvents.totalCount").value(1))
                .andExpect(jsonPath("$.data.contentMarkdown").value(org.hamcrest.Matchers.containsString("中文成长报告")));
        assertThat(jdbc.queryForObject("SELECT mastery_score FROM student_mastery WHERE student_id=?",
                java.math.BigDecimal.class, studentId)).isEqualByComparingTo(masteryBefore);
        assertThat(count("study_plan")).isEqualTo(plansBefore);
        assertThat(count("growth_event")).isEqualTo(eventsBefore);
        worker.run(Long.valueOf(reportId));
        assertThat(jdbc.queryForObject("SELECT version FROM growth_report WHERE id=?", Integer.class, reportId))
                .isEqualTo(3);
    }

    @Test
    void scenarios37_48_getListAndExportReadOnlyStoredSnapshot() throws Exception {
        seedReportData();
        String reportId = createPending("MONTHLY", "2026-07-01", "2026-07-31", "快照导出");
        worker.run(Long.valueOf(reportId));
        String stored = jdbc.queryForObject("SELECT statistics_snapshot_json FROM growth_report WHERE id=?",
                String.class, reportId);
        jdbc.update("INSERT INTO exam(student_id,exam_name,exam_type,exam_date,total_score,total_full_score) VALUES(?,'later','QUIZ','2026-07-20',90,100)", studentId);

        mvc.perform(get("/api/v1/reports").param("studentId", Long.toString(studentId))
                        .param("reportType", "MONTHLY").param("status", "SUCCESS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(reportId));
        mvc.perform(get("/api/v1/reports/{id}/export", reportId).param("format", "JSON"))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().bytes((stored + "\n").getBytes(StandardCharsets.UTF_8)))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("growth-report-" + reportId + ".json")));
        mvc.perform(get("/api/v1/reports/{id}/export", reportId).param("format", "MARKDOWN"))
                .andExpect(status().isOk()).andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("快照导出")));
        mvc.perform(get("/api/v1/reports/{id}/export", reportId).param("format", "PDF"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void scenarios49_56_cancelAndRecoveryRespectSafeStates() throws Exception {
        String pending = createPending("DAILY", "2026-07-01", "2026-07-01", "pending");
        mvc.perform(post("/api/v1/reports/{id}/cancel", pending).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mvc.perform(post("/api/v1/reports/{id}/cancel", pending).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        String running = createPending("DAILY", "2026-07-02", "2026-07-02", "running");
        jdbc.update("UPDATE growth_report SET status='RUNNING',start_time=NOW(3),progress_percent=10 WHERE id=?", running);
        mvc.perform(post("/api/v1/reports/{id}/cancel", running).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RUNNING"));
        ReflectionTestUtils.invokeMethod(worker, "markCancelled", Long.valueOf(running));
        assertThat(reportStatus(running)).isEqualTo("CANCELLED");

        String interrupted = createPending("DAILY", "2026-07-03", "2026-07-03", "interrupted");
        jdbc.update("UPDATE growth_report SET status='RUNNING',start_time=NOW(3),progress_percent=10 WHERE id=?", interrupted);
        recovery.onReady();
        assertThat(reportStatus(interrupted)).isEqualTo("PENDING");
    }

    @Test
    void scenarios57_63_regenerateCreatesTraceableImmutableHistory() throws Exception {
        String source = createPending("DAILY", "2026-07-01", "2026-07-01", "source");
        worker.run(Long.valueOf(source));
        String oldSnapshot = jdbc.queryForObject("SELECT statistics_snapshot_json FROM growth_report WHERE id=?",
                String.class, source);
        String child = json.readTree(mvc.perform(post("/api/v1/reports/{id}/regenerate", source)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"refresh\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.sourceReportId").value(source))
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asText();
        assertThat(child).isNotEqualTo(source);
        assertThat(jdbc.queryForObject("SELECT statistics_snapshot_json FROM growth_report WHERE id=?",
                String.class, source)).isEqualTo(oldSnapshot);
        mvc.perform(post("/api/v1/reports/{id}/regenerate", source)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        worker.run(Long.valueOf(child));
        assertThat(reportStatus(child)).isEqualTo("SUCCESS");
    }

    @Test
    void scenarios64_73_emptyDeletedFailureAndSafeErrors() throws Exception {
        jdbc.update("INSERT INTO growth_event(student_id,event_type,title,event_date,deleted) VALUES(?,'AWARD','deleted','2026-07-01',1)", studentId);
        String empty = createPending("DAILY", "2026-07-01", "2026-07-01", "empty");
        worker.run(Long.valueOf(empty));
        mvc.perform(get("/api/v1/reports/{id}", empty)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statisticsSnapshot.scores.examCount").value(0))
                .andExpect(jsonPath("$.data.statisticsSnapshot.growthEvents.totalCount").value(0));

        String failed = createPending("DAILY", "2026-07-02", "2026-07-02", "failed");
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        jdbc.update("DELETE FROM student WHERE id=?", studentId);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        worker.run(Long.valueOf(failed));
        assertThat(reportStatus(failed)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT error_message FROM growth_report WHERE id=?", String.class, failed))
                .isEqualTo("growth report generation failed");
        assertThat(jdbc.queryForObject("SELECT statistics_snapshot_json IS NULL FROM growth_report WHERE id=?",
                Boolean.class, failed)).isTrue();
    }

    @Test
    void scenarios74_100_allOperationsImplementedAndMigrationStateStable() throws Exception {
        String spec = Files.readString(Path.of("api", "openapi.yaml"), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("operationId:\\s*([A-Za-z0-9_]+)").matcher(spec);
        java.util.Set<String> operations = new java.util.HashSet<>();
        while (matcher.find()) operations.add(matcher.group(1));
        Set<String> implemented = java.util.Arrays.stream(GeneratedApiController.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet());
        assertThat(operations).hasSize(116);
        assertThat(implemented).containsAll(operations);
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("23");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'",
                Integer.class)).isEqualTo(47);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_config", Integer.class)).isEqualTo(31);
        doThrow(new IllegalStateException("test rescan failure")).when(dispatcher).dispatch(anyLong());
        recovery.rescanPending();
    }

    private String createPending(String type, String start, String end, String title) throws Exception {
        JsonNode body = json.readTree(mvc.perform(post("/api/v1/reports").contentType(MediaType.APPLICATION_JSON)
                        .content(request(studentId, type, start, end, title)))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.id").isString()).andReturn().getResponse().getContentAsString());
        return body.path("data").path("id").asText();
    }

    private String request(long student, String type, String start, String end, String title) throws Exception {
        return json.writeValueAsString(java.util.Map.of("studentId", Long.toString(student), "reportType", type,
                "title", title, "startDate", start, "endDate", end));
    }

    private void seedReportData() {
        long subjectId = jdbc.queryForObject("SELECT id FROM subject ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO exam(student_id,exam_name,exam_type,exam_date,total_score,total_full_score) VALUES(?,'月考','MONTHLY','2026-07-10',80,100)", studentId);
        long examId = jdbc.queryForObject("SELECT id FROM exam WHERE student_id=?", Long.class, studentId);
        jdbc.update("INSERT INTO score_record(exam_id,student_id,subject_id,score,full_score) VALUES(?,?,?,?,?)",
                examId, studentId, subjectId, 80, 100);
        jdbc.update("INSERT INTO knowledge_node(node_code,name,node_type,subject_id) VALUES('STAGE12F-K','测试知识点','POINT',?)", subjectId);
        long knowledgeId = jdbc.queryForObject("SELECT id FROM knowledge_node WHERE node_code='STAGE12F-K'", Long.class);
        jdbc.update("INSERT INTO student_mastery(student_id,knowledge_id,mastery_score,next_review_time) VALUES(?,?,55,'2026-07-15')",
                studentId, knowledgeId);
        jdbc.update("INSERT INTO wrong_question(student_id,subject_id,source_type,question_text,status,occurred_date,next_review_time) VALUES(?,?,'PRACTICE','错题','NEW','2026-07-11','2026-07-20')",
                studentId, subjectId);
        jdbc.update("INSERT INTO study_log(student_id,subject_id,study_date,duration_seconds) VALUES(?,?, '2026-07-12',1800)",
                studentId, subjectId);
        jdbc.update("INSERT INTO growth_event(student_id,event_type,title,event_date) VALUES(?,'AWARD','获得进步奖','2026-07-13')",
                studentId);
        jdbc.update("INSERT INTO study_plan(student_id,title,plan_type,start_date,end_date,status) VALUES(?,'七月计划','MONTHLY','2026-07-01','2026-07-31','ACTIVE')",
                studentId);
        long planId = jdbc.queryForObject("SELECT id FROM study_plan WHERE student_id=?", Long.class, studentId);
        jdbc.update("INSERT INTO study_plan_task(study_plan_id,task_date,task_type,title,status) VALUES(?,'2026-07-14','OTHER','任务','TODO')",
                planId);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Integer.class);
    }

    private String reportStatus(String id) {
        return jdbc.queryForObject("SELECT status FROM growth_report WHERE id=?", String.class, id);
    }

}
