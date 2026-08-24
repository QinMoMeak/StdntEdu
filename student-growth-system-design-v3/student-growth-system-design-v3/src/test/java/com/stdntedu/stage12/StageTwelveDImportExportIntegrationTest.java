package com.stdntedu.stage12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.transfer.service.TransferRecoveryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
class StageTwelveDImportExportIntegrationTest {
    private static final Path STORAGE_ROOT = Path.of("target", "stdntedu-stage12d-" + UUID.randomUUID())
            .toAbsolutePath();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.36").asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("student_growth").withUsername("student_growth").withPassword("student_growth");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.ai.extraction.storage-root", STORAGE_ROOT::toString);
        registry.add("app.ai.extraction.pending-rescan.initial-delay-ms", () -> 3_600_000);
        registry.add("app.ai.study-plan.pending-rescan.initial-delay-ms", () -> 3_600_000);
        registry.add("app.transfer.pending-rescan.initial-delay-ms", () -> 3_600_000);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired TransferRecoveryService recovery;
    private long stageId;
    private long gradeId;
    private long studentId;

    @BeforeEach
    void clean() throws Exception {
        jdbc.update("DELETE FROM import_task");
        jdbc.update("DELETE FROM export_task");
        jdbc.update("DELETE FROM attachment WHERE id NOT IN (SELECT attachment_id FROM ai_extraction_file)");
        jdbc.update("DELETE FROM wrong_question_knowledge");
        jdbc.update("DELETE FROM wrong_review");
        jdbc.update("DELETE FROM wrong_question");
        jdbc.update("DELETE FROM academic_term");
        jdbc.update("DELETE FROM student WHERE student_code LIKE 'S12D-%' OR name LIKE 'Stage12D%' OR name LIKE 'Imported%' OR name LIKE '=cmd%'");
        deleteTree(STORAGE_ROOT);
        Files.createDirectories(STORAGE_ROOT);
        stageId = jdbc.queryForObject("SELECT id FROM stage WHERE enabled=1 ORDER BY id LIMIT 1", Long.class);
        gradeId = jdbc.queryForObject("SELECT id FROM grade WHERE stage_id=? AND enabled=1 ORDER BY id LIMIT 1",
                Long.class, stageId);
        jdbc.update("INSERT INTO student(student_code,name,deleted,version) VALUES(?, 'Stage12D Owner',0,0)",
                "S12D-" + UUID.randomUUID().toString().substring(0, 12));
        studentId = jdbc.queryForObject("SELECT id FROM student WHERE name='Stage12D Owner' ORDER BY id DESC LIMIT 1",
                Long.class);
    }

    @AfterAll
    static void cleanup() throws Exception { deleteTree(STORAGE_ROOT); }

    @Test
    void scenarios01_12_uploadParsesPreviewWithoutWritingAndReturnsStringIds() throws Exception {
        String taskId = createStudentImport("Imported Preview");
        await("import_task", taskId, "PREVIEW_READY");

        mvc.perform(get("/api/v1/imports/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isString())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.status").value("PREVIEW_READY"))
                .andExpect(jsonPath("$.data.validRows").value(1))
                .andExpect(jsonPath("$.data.invalidRows").value(0))
                .andExpect(jsonPath("$.data.preview.rows[0].data.name").value("Imported Preview"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE name='Imported Preview'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT storage_path FROM attachment a JOIN import_task i ON i.attachment_id=a.id WHERE i.id=?",
                String.class, taskId)).startsWith(STORAGE_ROOT.toString());
    }

    @Test
    void scenarios13_26_confirmIsTransactionalAsynchronousAndIdempotent() throws Exception {
        String taskId = createStudentImport("Imported Confirmed");
        await("import_task", taskId, "PREVIEW_READY");
        String key = "stage12d-confirm-0001";
        mvc.perform(post("/api/v1/imports/{id}/confirm", taskId)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted());
        await("import_task", taskId, "SUCCESS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE name='Imported Confirmed'", Integer.class))
                .isEqualTo(1);

        mvc.perform(post("/api/v1/imports/{id}/confirm", taskId)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE name='Imported Confirmed'", Integer.class))
                .isEqualTo(1);
        mvc.perform(post("/api/v1/imports/{id}/confirm", taskId)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmWarnings\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void scenarios27_36_invalidRowsProducePersistentSafeReportAndBlockDefaultConfirm() throws Exception {
        String payload = "[{\"name\":\"Missing scope\"}]";
        String taskId = createImport(payload);
        await("import_task", taskId, "PREVIEW_READY");
        mvc.perform(get("/api/v1/imports/{id}", taskId))
                .andExpect(jsonPath("$.data.invalidRows").value(1))
                .andExpect(jsonPath("$.data.errorReportAvailable").value(true));
        mvc.perform(get("/api/v1/imports/{id}/errors", taskId))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("VALIDATION_ERROR")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
        mvc.perform(post("/api/v1/imports/{id}/confirm", taskId)
                        .header("Idempotency-Key", "stage12d-invalid-0001")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void scenarios37_44_cancelAndRetryUseFrozenTransitions() throws Exception {
        String ready = createStudentImport("Imported Cancelled");
        await("import_task", ready, "PREVIEW_READY");
        mvc.perform(post("/api/v1/imports/{id}/cancel", ready)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mvc.perform(post("/api/v1/imports/{id}/confirm", ready)
                        .header("Idempotency-Key", "stage12d-cancelled-01")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());

        String failed = createImport("not-json");
        await("import_task", failed, "FAILED");
        jdbc.update("UPDATE import_task SET idempotency_key='stage12d-old-key',confirm_request_json='{}',confirm_request_hash=REPEAT('a',64) WHERE id=?", failed);
        mvc.perform(post("/api/v1/imports/{id}/retry", failed)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted());
        await("import_task", failed, "FAILED");
        assertThat(jdbc.queryForObject("SELECT retry_count FROM import_task WHERE id=?", Integer.class, failed))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT idempotency_key FROM import_task WHERE id=?", String.class, failed))
                .isNull();
    }

    @Test
    void uploadRejectsMimeSpoofing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "students.json", "text/csv", "[]".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/imports").file(file).param("importType", "STUDENT"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void scenarios45_60_exportJsonPersistsStreamsAndExcludesPrivateInfrastructure() throws Exception {
        String taskId = createExport("JSON", "[\"STUDENT\"]", studentId);
        await("export_task", taskId, "SUCCESS");
        byte[] body = mvc.perform(get("/api/v1/exports/{id}/download", taskId))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn().getResponse().getContentAsByteArray();
        JsonNode document = json.readTree(body);
        assertThat(document.path("STUDENT").get(0).path("id").asText()).isEqualTo(Long.toString(studentId));
        assertThat(new String(body, StandardCharsets.UTF_8)).doesNotContain("api_key", "secret", "system_config");
        String stored = jdbc.queryForObject("""
                SELECT a.storage_path FROM export_task e JOIN attachment a ON a.id=e.output_attachment_id
                WHERE e.id=?
                """, String.class, taskId);
        assertThat(stored).startsWith(STORAGE_ROOT.toString());
        assertThat(Files.exists(Path.of(stored))).isTrue();
    }

    @Test
    void scenarios61_69_csvEscapesFormulaAndUsesStablePublicColumns() throws Exception {
        jdbc.update("UPDATE student SET name='=cmd|test' WHERE id=?", studentId);
        String taskId = createExport("CSV", "[\"STUDENT\"]", studentId);
        await("export_task", taskId, "SUCCESS");
        String csv = mvc.perform(get("/api/v1/exports/{id}/download", taskId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("id,studentCode,name,birthday,school");
        assertThat(csv).contains("'=cmd|test");
        assertThat(csv).doesNotContain("storage_path", "password");
    }

    @Test
    void exportXlsxUsesFrozenMimeAndPersistentArtifact() throws Exception {
        String taskId = createExport("XLSX", "[\"STUDENT\"]", studentId);
        await("export_task", taskId, "SUCCESS");
        byte[] body = mvc.perform(get("/api/v1/exports/{id}/download", taskId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).startsWith((byte) 'P', (byte) 'K');
    }

    @Test
    void scenarios70_80_exportValidationListGetAndNotReadyDownload() throws Exception {
        mvc.perform(post("/api/v1/exports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportTypes\":[\"STUDENT\"],\"format\":\"JSON\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/exports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + studentId + "\",\"exportTypes\":[\"STUDENT\",\"SCORE\"],\"format\":\"CSV\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/exports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + studentId + "\",\"exportTypes\":[\"STUDENT\"],\"format\":\"JSON\",\"includeAttachments\":true}"))
                .andExpect(status().isUnprocessableEntity());

        jdbc.update("INSERT INTO export_task(task_code,student_id,export_types_json,export_format,status,filter_json) VALUES(?,?,JSON_ARRAY('STUDENT'),'JSON','PENDING','{}')",
                "EXP-PENDING-" + UUID.randomUUID(), studentId);
        long pending = jdbc.queryForObject("SELECT id FROM export_task WHERE task_code LIKE 'EXP-PENDING-%' ORDER BY id DESC LIMIT 1", Long.class);
        mvc.perform(get("/api/v1/exports/{id}/download", pending)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/exports").param("studentId", Long.toString(studentId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].taskId").isString());
    }

    @Test
    void scenarios81_89_recoveryResetsOnlySafeRunningStages() {
        jdbc.update("INSERT INTO import_task(task_code,import_type,status,options_json) VALUES('IMP-REC-V','STUDENT','VALIDATING','{}')");
        jdbc.update("INSERT INTO import_task(task_code,import_type,status,options_json) VALUES('IMP-REC-I','STUDENT','IMPORTING','{}')");
        jdbc.update("INSERT INTO export_task(task_code,export_types_json,export_format,status,filter_json) VALUES('EXP-REC',JSON_ARRAY('STUDENT'),'JSON','RUNNING','{}')");
        recovery.onReady();
        assertThat(jdbc.queryForObject("SELECT status FROM import_task WHERE task_code='IMP-REC-V'", String.class))
                .isIn("UPLOADED", "VALIDATING", "FAILED");
        assertThat(jdbc.queryForObject("SELECT status FROM import_task WHERE task_code='IMP-REC-I'", String.class))
                .isIn("CONFIRM_PENDING", "IMPORTING", "FAILED");
        assertThat(jdbc.queryForObject("SELECT status FROM export_task WHERE task_code='EXP-REC'", String.class))
                .isIn("PENDING", "RUNNING", "FAILED");
    }

    @Test
    void wrongQuestionImportRemainsBackwardCompatibleValidatesQuestionTypeAndExportsIt() throws Exception {
        long subjectId = jdbc.queryForObject("SELECT id FROM subject WHERE enabled=1 ORDER BY id LIMIT 1", Long.class);
        String legacy = createWrongQuestionImport(subjectId, "legacy type", null);
        await("import_task", legacy, "PREVIEW_READY");
        confirm(legacy, "stage12d-wrong-legacy");
        await("import_task", legacy, "SUCCESS");
        assertThat(jdbc.queryForObject("SELECT question_type FROM wrong_question WHERE question_text='legacy type'",
                String.class)).isNull();

        String typed = createWrongQuestionImport(subjectId, "typed import", "SHORT_ANSWER");
        await("import_task", typed, "PREVIEW_READY");
        confirm(typed, "stage12d-wrong-typed");
        await("import_task", typed, "SUCCESS");
        assertThat(jdbc.queryForObject("SELECT question_type FROM wrong_question WHERE question_text='typed import'",
                String.class)).isEqualTo("SHORT_ANSWER");

        String invalid = createWrongQuestionImport(subjectId, "invalid type", "NOT_A_QUESTION_TYPE");
        await("import_task", invalid, "PREVIEW_READY");
        confirm(invalid, "stage12d-wrong-invalid");
        await("import_task", invalid, "FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrong_question WHERE question_text='invalid type'",
                Integer.class)).isZero();

        String export = createExport("JSON", "[\"WRONG_QUESTION\"]", studentId);
        await("export_task", export, "SUCCESS");
        JsonNode document = json.readTree(mvc.perform(get("/api/v1/exports/{id}/download", export))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(document.path("WRONG_QUESTION").get(0).path("questionType").isNull()).isTrue();
        assertThat(document.path("WRONG_QUESTION").get(1).path("questionType").asText())
                .isEqualTo("SHORT_ANSWER");
    }

    private String createStudentImport(String name) throws Exception {
        return createImport("[{\"name\":\"" + name + "\",\"currentStageId\":\"" + stageId
                + "\",\"currentGradeId\":\"" + gradeId + "\"}]");
    }

    private String createImport(String payload) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "students.json", "application/json",
                payload.getBytes(StandardCharsets.UTF_8));
        String response = mvc.perform(multipart("/api/v1/imports").file(file).param("importType", "STUDENT"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("taskId").asText();
    }

    private String createWrongQuestionImport(long subjectId, String text, String questionType) throws Exception {
        String type = questionType == null ? "" : ",\"questionType\":\"" + questionType + "\"";
        String payload = "[{\"studentId\":\"" + studentId + "\",\"subjectId\":\"" + subjectId
                + "\",\"sourceType\":\"PRACTICE\",\"questionText\":\"" + text + "\"" + type + "}]";
        MockMultipartFile file = new MockMultipartFile("file", "wrong-questions.json", "application/json",
                payload.getBytes(StandardCharsets.UTF_8));
        String response = mvc.perform(multipart("/api/v1/imports").file(file)
                        .param("importType", "WRONG_QUESTION").param("studentId", Long.toString(studentId)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("taskId").asText();
    }

    private void confirm(String taskId, String key) throws Exception {
        mvc.perform(post("/api/v1/imports/{id}/confirm", taskId).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted());
    }

    private String createExport(String format, String types, long owner) throws Exception {
        String response = mvc.perform(post("/api/v1/exports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + owner + "\",\"exportTypes\":" + types
                                + ",\"format\":\"" + format + "\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("taskId").asText();
    }

    private void await(String table, String id, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            String status = jdbc.queryForObject("SELECT status FROM " + table + " WHERE id=?", String.class, id);
            if (expected.equals(status)) return;
            Thread.sleep(50);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM " + table + " WHERE id=?", String.class, id))
                .isEqualTo(expected);
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        }
    }
}
