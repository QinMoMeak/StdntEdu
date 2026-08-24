package com.stdntedu.stage12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.model.security.AiSecretCryptoService;
import com.stdntedu.backup.service.BackupRestoreRecoveryService;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class StageTwelveEBackupRestoreIntegrationTest {
    private static final Path STORAGE_ROOT = Path.of("target", "stdntedu-stage12e-" + UUID.randomUUID())
            .toAbsolutePath();
    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(new byte[32]);
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
        registry.add("STDNTEDU_AI_SECRET_MASTER_KEY", () -> MASTER_KEY);
        registry.add("app.ai.extraction.storage-root", STORAGE_ROOT::toString);
        registry.add("app.ai.extraction.pending-rescan.initial-delay-ms", () -> 3_600_000);
        registry.add("app.ai.study-plan.pending-rescan.initial-delay-ms", () -> 3_600_000);
        registry.add("app.transfer.pending-rescan.initial-delay-ms", () -> 3_600_000);
        registry.add("app.backup-restore.pending-rescan.initial-delay-ms", () -> 3_600_000);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired AiSecretCryptoService crypto;
    @Autowired BackupRestoreRecoveryService recovery;
    private long studentId;

    @BeforeEach
    void clean() throws Exception {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                for (String table : USER_TABLES) statement.executeUpdate("DELETE FROM `" + table + "`");
                statement.execute("SET FOREIGN_KEY_CHECKS=1");
            }
            return null;
        });
        deleteTree(STORAGE_ROOT);
        Files.createDirectories(STORAGE_ROOT);
        studentId = insertStudent("Stage12E Original");
    }

    @AfterAll static void cleanup() throws Exception { deleteTree(STORAGE_ROOT); }

    @Test
    void scenarios01_25_backupCreatesStablePersistentVerifiedPackage() throws Exception {
        long attachmentId = insertAttachment("stage12e-content".getBytes(StandardCharsets.UTF_8));
        String backupId = createBackup(true, "EXCLUDE");
        await("backup_record", backupId, "SUCCESS");

        mvc.perform(get("/api/v1/backups/{id}", backupId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(backupId))
                .andExpect(jsonPath("$.data.format").value("STDNTEDU_BACKUP_V1"))
                .andExpect(jsonPath("$.data.databaseVersion").value("23"))
                .andExpect(jsonPath("$.data.attachmentCount").value(1));
        mvc.perform(post("/api/v1/backups/{id}/verify", backupId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.zipChecksumMatched").value(true))
                .andExpect(jsonPath("$.data.manifestValid").value(true));
        byte[] archive = mvc.perform(get("/api/v1/backups/{id}/download", backupId))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/zip"))
                .andExpect(header().exists("Content-Length")).andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("stdntedu-backup-")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(archive).startsWith((byte) 'P', (byte) 'K');
        Path stored = backupPath(backupId);
        assertThat(stored).startsWith(STORAGE_ROOT);
        assertThat(Files.exists(stored)).isTrue();
        JsonNode manifest = manifest(stored);
        assertThat(manifest.path("format").asText()).isEqualTo("STDNTEDU_BACKUP_V1");
        assertThat(manifest.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(manifest.path("openapiVersion").asText()).isEqualTo("3.15.0");
        assertThat(manifest.path("timezone").asText()).isEqualTo("Asia/Shanghai");
        assertThat(manifest.path("datasets").toString()).doesNotContain("backup_record", "restore_record",
                "import_task", "export_task", "system_config", "flyway_schema_history");
        assertThat(manifest.path("attachments").get(0).path("attachmentId").asLong()).isEqualTo(attachmentId);
        assertThat(new String(archive, StandardCharsets.ISO_8859_1)).doesNotContain(MASTER_KEY);
    }

    @Test
    void scenarios26_39_verifyDetectsCorruptionAndDeleteIsTruthful() throws Exception {
        String backupId = createBackup(false, "EXCLUDE");
        await("backup_record", backupId, "SUCCESS");
        Path path = backupPath(backupId);
        byte[] bytes = Files.readAllBytes(path);
        bytes[bytes.length / 2] ^= 1;
        Files.write(path, bytes);
        jdbc.update("UPDATE backup_record SET checksum=? WHERE id=?", sha256(path), backupId);
        mvc.perform(post("/api/v1/backups/{id}/verify", backupId)).andExpect(status().is5xxServerError());

        String deletable = createBackup(false, "EXCLUDE");
        await("backup_record", deletable, "SUCCESS");
        Path artifact = backupPath(deletable);
        mvc.perform(delete("/api/v1/backups/{id}", deletable)).andExpect(status().isNoContent());
        assertThat(Files.exists(artifact)).isFalse();
        mvc.perform(get("/api/v1/backups/{id}", deletable)).andExpect(status().isNotFound());
    }

    @Test
    void scenarios40_57_restoreReplacesDataPreservesIdsAndKeepsSystemAuthority() throws Exception {
        long originalId = studentId;
        long attachmentId = insertAttachment("restore-me".getBytes(StandardCharsets.UTF_8));
        Path oldAttachment = Path.of(jdbc.queryForObject("SELECT storage_path FROM attachment WHERE id=?",
                String.class, attachmentId));
        String backupId = createBackup(true, "EXCLUDE");
        await("backup_record", backupId, "SUCCESS");
        mvc.perform(post("/api/v1/backups/{id}/verify", backupId)).andExpect(status().isOk());

        jdbc.update("UPDATE student SET name='Mutated' WHERE id=?", originalId);
        insertStudent("Extra after backup");
        jdbc.update("DELETE FROM attachment WHERE id=?", attachmentId);
        Files.delete(oldAttachment);
        String restoreId = createRestore(backupId, true, false);
        await("restore_record", restoreId, "SUCCESS");
        assertThat(jdbc.queryForObject("SELECT name FROM student WHERE id=?", String.class, originalId))
                .isEqualTo("Stage12E Original");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE name='Extra after backup'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_config", Integer.class)).isEqualTo(31);
        Path restoredAttachment = Path.of(jdbc.queryForObject("SELECT storage_path FROM attachment WHERE id=?",
                String.class, attachmentId));
        assertThat(Files.readString(restoredAttachment)).isEqualTo("restore-me");
        assertThat(jdbc.queryForObject("SELECT sha256 FROM attachment WHERE id=?", String.class, attachmentId))
                .isEqualTo(sha256(restoredAttachment));
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("23");
        long next = insertStudent("After restore");
        assertThat(next).isGreaterThan(originalId);
        mvc.perform(get("/api/v1/restores/{id}", restoreId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(restoreId))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.progressPercent").value(100));
    }

    @Test
    void scenarios58_66_secretModesNeverExposeMasterKeyAndRestoreReferencesStayValid() throws Exception {
        insertAiModelWithSecret();
        String excluded = createBackup(false, "EXCLUDE");
        await("backup_record", excluded, "SUCCESS");
        JsonNode excludedManifest = manifest(backupPath(excluded));
        assertThat(excludedManifest.path("masterKeyFingerprint").isNull()).isTrue();
        assertThat(excludedManifest.path("datasets").toString()).doesNotContain("ai_secret");

        String included = createBackup(false, "INCLUDE_ENCRYPTED");
        await("backup_record", included, "SUCCESS");
        JsonNode includedManifest = manifest(backupPath(included));
        assertThat(includedManifest.path("masterKeyFingerprint").asText()).isEqualTo(crypto.masterKeyFingerprint());
        assertThat(includedManifest.path("datasets").toString()).contains("ai_secret");
        byte[] archive = Files.readAllBytes(backupPath(included));
        assertThat(new String(archive, StandardCharsets.ISO_8859_1)).doesNotContain(MASTER_KEY);
        mvc.perform(post("/api/v1/backups/{id}/verify", included)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/backups/{id}/verify", excluded)).andExpect(status().isOk());
        String restoreId = createRestore(excluded, false, false);
        await("restore_record", restoreId, "SUCCESS");
        assertThat(jdbc.queryForObject("SELECT api_key_ref FROM ai_model LIMIT 1", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_secret", Integer.class)).isZero();

        String secretRestoreId = createRestore(included, false, true);
        await("restore_record", secretRestoreId, "SUCCESS");
        assertThat(jdbc.queryForObject("SELECT api_key_ref FROM ai_model LIMIT 1", String.class))
                .isEqualTo("stage12e-secret");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_secret", Integer.class)).isOne();
    }

    @Test
    void scenarios67_74_restoreCancellationHonorsSafeCheckpoints() throws Exception {
        String backupId = successfulVerifiedBackup();
        long pending = insertRestore(backupId, "PENDING", "QUEUED", false);
        mvc.perform(post("/api/v1/restores/{id}/cancel", pending).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));

        long staging = insertRestore(backupId, "RUNNING", "STAGING", false);
        mvc.perform(post("/api/v1/restores/{id}/cancel", staging).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cancelRequested").value(true));
        long applying = insertRestore(backupId, "RUNNING", "APPLYING", false);
        mvc.perform(post("/api/v1/restores/{id}/cancel", applying).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TASK_STATE_CONFLICT"));
    }

    @Test
    void interruptedTasksRecoverOnlyFromSafePersistentCheckpoints() throws Exception {
        String backupId = successfulVerifiedBackup();
        jdbc.update("INSERT INTO backup_record(backup_code,backup_type,status,start_time) VALUES(?,'FULL','RUNNING',NOW(3))",
                "BKP-INTERRUPTED-" + UUID.randomUUID());
        long interruptedBackup = jdbc.queryForObject("SELECT id FROM backup_record WHERE status='RUNNING'", Long.class);
        long interruptedRestore = insertRestore(backupId, "RUNNING", "STAGING", false);
        jdbc.update("UPDATE restore_record SET checkpoint_json='{\"newPaths\":{},\"oldPaths\":[]}' WHERE id=?",
                interruptedRestore);

        ReflectionTestUtils.invokeMethod(recovery, "recoverInterrupted");
        assertThat(jdbc.queryForObject("SELECT status FROM backup_record WHERE id=?", String.class,
                interruptedBackup)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT progress_stage FROM restore_record WHERE id=?", String.class,
                interruptedRestore)).isEqualTo("QUEUED");
        assertThat(jdbc.queryForObject("SELECT checkpoint_json IS NULL FROM restore_record WHERE id=?", Boolean.class,
                interruptedRestore)).isTrue();

        jdbc.update("DELETE FROM restore_record WHERE id=?", interruptedRestore);
        jdbc.update("DELETE FROM backup_record WHERE id=?", interruptedBackup);
        long finalizing = insertRestore(backupId, "RUNNING", "FINALIZING", true);
        jdbc.update("UPDATE restore_record SET checkpoint_json='{\"newPaths\":{},\"oldPaths\":[]}' WHERE id=?",
                finalizing);
        recovery.rescanPending();
        await("restore_record", Long.toString(finalizing), "SUCCESS");
    }

    @Test
    void scenarios75_82_listValidationMutualExclusionAndStringIds() throws Exception {
        jdbc.update("INSERT INTO backup_record(backup_code,backup_type,status) VALUES(?, 'FULL','PENDING')",
                "BKP-BUSY-" + UUID.randomUUID());
        mvc.perform(post("/api/v1/backups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backupType\":\"FULL\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/backups").param("startTime", "2026-01-02T00:00:00+08:00")
                        .param("endTime", "2026-01-01T00:00:00+08:00"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/backups")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").isString());
    }

    private String successfulVerifiedBackup() throws Exception {
        String id = createBackup(false, "EXCLUDE");
        await("backup_record", id, "SUCCESS");
        mvc.perform(post("/api/v1/backups/{id}/verify", id)).andExpect(status().isOk());
        return id;
    }

    private String createBackup(boolean attachments, String secretMode) throws Exception {
        String body = "{\"backupType\":\"FULL\",\"includeAttachments\":" + attachments
                + ",\"secretMode\":\"" + secretMode + "\"}";
        String response = mvc.perform(post("/api/v1/backups").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("id").asText();
    }

    private String createRestore(String backupId, boolean attachments, boolean secrets) throws Exception {
        byte[] body = json.writeValueAsBytes(java.util.Map.of(
                "confirmationText", "\u786e\u8ba4\u6062\u590d",
                "restoreAttachments", attachments,
                "restoreAiSecrets", secrets));
        String response = mvc.perform(post("/api/v1/backups/{id}/restore", backupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("taskId").asText();
    }

    private long insertStudent(String name) {
        jdbc.update("INSERT INTO student(student_code,name,deleted,version) VALUES(?,?,0,0)",
                "S12E-" + UUID.randomUUID().toString().substring(0, 12), name);
        return jdbc.queryForObject("SELECT id FROM student WHERE name=? ORDER BY id DESC LIMIT 1", Long.class, name);
    }

    private long insertAttachment(byte[] content) throws Exception {
        Path path = STORAGE_ROOT.resolve(UUID.randomUUID() + ".bin");
        Files.write(path, content);
        String hash = sha256(path);
        jdbc.update("INSERT INTO attachment(file_name,storage_type,storage_path,mime_type,file_size,sha256,deleted) VALUES('note.bin','LOCAL',?,'application/octet-stream',?,?,0)",
                path.toString(), content.length, hash);
        return jdbc.queryForObject("SELECT id FROM attachment ORDER BY id DESC LIMIT 1", Long.class);
    }

    private void insertAiModelWithSecret() {
        char[] value = "stage12e-secret-value".toCharArray();
        var encrypted = crypto.encrypt("stage12e-secret", value);
        jdbc.update("INSERT INTO ai_secret(secret_ref,encrypted_value,nonce,algorithm,key_version,mask_suffix) VALUES(?,?,?,?,?,?)",
                "stage12e-secret", encrypted.encryptedValue(), encrypted.nonce(), encrypted.algorithm(),
                encrypted.keyVersion(), "alue");
        jdbc.update("""
                INSERT INTO ai_model(name,provider,model_type,model_name,protocol,auth_type,api_base_url,
                    api_key_ref,supports_vision,supports_json,local_flag,enabled,priority_no,timeout_seconds,version)
                VALUES('Stage12E','OPENAI','CHAT','stage12e-model','OPENAI_COMPATIBLE','BEARER_API_KEY',
                    'https://example.invalid','stage12e-secret',0,1,0,1,100,120,0)
                """);
    }

    private long insertRestore(String backupId, String status, String phase, boolean databaseApplied) {
        jdbc.update("""
                INSERT INTO restore_record(restore_code,backup_id,status,progress_stage,progress_percent,options_json,
                    cancel_requested,database_applied,files_finalized,start_time,finish_time)
                VALUES(?,?,?,?,0,'{\"restoreAttachments\":false,\"restoreAiSecrets\":false,\"conflictStrategy\":\"REPLACE\"}',
                    0,?,0,IF(?='PENDING',NULL,NOW(3)),NULL)
                """, "RST-" + UUID.randomUUID(), backupId, status, phase, databaseApplied, status);
        return jdbc.queryForObject("SELECT id FROM restore_record ORDER BY id DESC LIMIT 1", Long.class);
    }

    private void await(String table, String id, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            String state = jdbc.queryForObject("SELECT status FROM " + table + " WHERE id=?", String.class, id);
            if (expected.equals(state)) return;
            if ("FAILED".equals(state) && !"FAILED".equals(expected)) break;
            Thread.sleep(50);
        }
        String state = jdbc.queryForObject("SELECT status FROM " + table + " WHERE id=?", String.class, id);
        String error = jdbc.queryForObject("SELECT COALESCE(error_message,'') FROM " + table + " WHERE id=?",
                String.class, id);
        assertThat(state).withFailMessage("task ended as %s: %s", state, error).isEqualTo(expected);
    }

    private Path backupPath(String id) { return Path.of(jdbc.queryForObject(
            "SELECT storage_path FROM backup_record WHERE id=?", String.class, id)); }

    private JsonNode manifest(Path archive) throws Exception {
        try (ZipFile zip = ZipFile.builder().setPath(archive).get();
                var input = zip.getInputStream(zip.getEntry("backup-manifest.json"))) {
            return json.readTree(input);
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(path));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        }
    }
}
