package com.stdntedu.baseline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class FlywayMigrationIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(mysqlImage())
            .withDatabaseName("student_growth")
            .withUsername("student_growth")
            .withPassword("student_growth");

    private static DockerImageName mysqlImage() {
        String image = System.getenv().getOrDefault("TEST_MYSQL_IMAGE", "mysql:8.0.36");
        return DockerImageName.parse(image).asCompatibleSubstituteFor("mysql");
    }

    @Test
    void migratesEmptyDatabaseAndValidatesBaseline() throws Exception {
        Flyway baselineFlyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("13"))
                .load();

        baselineFlyway.migrate();

        List<String> originalConfigKeys;
        List<String> schemaBeforeV14;
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals("13", baselineFlyway.info().current().getVersion().toString());
            assertEquals(42, countBusinessTables(connection));
            assertExistingAlgorithmConfig(connection);
            originalConfigKeys = queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key");
            schemaBeforeV14 = schemaSignature(connection);
        }

        Flyway v14Flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("14"))
                .load();

        v14Flyway.migrate();

        List<String> businessTablesBeforeV15;
        List<String> configKeysBeforeV15;
        List<String> schemaBeforeV15;
        List<String> studyLogColumnsBeforeV15;
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals("14", v14Flyway.info().current().getVersion().toString());
            assertEquals(42, countBusinessTables(connection));
            assertAlgorithmConfig(connection);
            assertTrue(queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key")
                    .containsAll(originalConfigKeys));
            assertEquals(schemaBeforeV14, schemaSignature(connection));
            businessTablesBeforeV15 = businessTableNames(connection);
            configKeysBeforeV15 = queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key");
            schemaBeforeV15 = schemaSignatureWithoutStudyLogVersion(connection);
            studyLogColumnsBeforeV15 = studyLogColumnNames(connection);
            assertFalse(studyLogColumnsBeforeV15.contains("version"));
        }

        Flyway v15Flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("15"))
                .load();

        v15Flyway.migrate();

        List<String> businessTablesBeforeV16;
        List<String> configKeysBeforeV16;
        List<String> schemaBeforeV16;
        Map<String, Integer> v1ToV15Checksums;

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals("15", v15Flyway.info().current().getVersion().toString());
            assertEquals(42, countBusinessTables(connection));
            assertBasicData(connection);
            assertDictionaryData(connection);
            assertAlgorithmConfig(connection);
            assertEquals(businessTablesBeforeV15, businessTableNames(connection));
            assertEquals(configKeysBeforeV15,
                    queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key"));
            assertEquals(schemaBeforeV15, schemaSignatureWithoutStudyLogVersion(connection));
            List<String> studyLogColumnsAfterV15 = studyLogColumnNames(connection);
            assertTrue(studyLogColumnsAfterV15.contains("version"));
            assertEquals(studyLogColumnsBeforeV15,
                    studyLogColumnsAfterV15.stream().filter(column -> !"version".equals(column)).toList());
            assertStudyLogVersionDefinition(connection);
            assertSchemaFullStudyLogMatches(connection);
            assertConstraints(connection);
            businessTablesBeforeV16 = businessTableNames(connection);
            configKeysBeforeV16 = queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key");
            schemaBeforeV16 = schemaSignature(connection);
            v1ToV15Checksums = appliedChecksums(v15Flyway, 15);
        }

        Flyway v16Flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("16"))
                .load();

        v16Flyway.migrate();

        List<String> businessTablesBeforeV17;
        List<String> configKeysBeforeV17;
        List<String> schemaBeforeV17;
        Map<String, Integer> v1ToV16Checksums;
        List<String> businessTablesBeforeV18;
        List<String> configKeysBeforeV18;
        List<String> schemaBeforeV18;
        Map<String, Integer> v1ToV17Checksums;
        List<String> businessTablesBeforeV19;
        List<String> configKeysBeforeV19;
        List<String> nonAiSchemaBeforeV19;
        Map<String, Integer> v1ToV18Checksums;

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals("16", v16Flyway.info().current().getVersion().toString());
            assertEquals(v1ToV15Checksums, appliedChecksums(v16Flyway, 15));
            assertEquals(43, countBusinessTables(connection));
            assertEquals(31, queryInt(connection, "SELECT COUNT(*) FROM system_config"));
            assertEquals(configKeysBeforeV16,
                    queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key"));
            List<String> businessTablesAfterV16 = businessTableNames(connection);
            assertEquals(businessTablesBeforeV16.size() + 1, businessTablesAfterV16.size());
            assertTrue(businessTablesAfterV16.containsAll(businessTablesBeforeV16));
            assertTrue(businessTablesAfterV16.contains("student_resource_assignment"));
            assertEquals(schemaBeforeV16, schemaSignatureWithoutStudentResourceAssignment(connection));
            assertStudentResourceAssignmentDefinition(connection);
            assertSchemaFullStudentResourceAssignmentMatches(connection);
            assertV16MigrationCopiesMatch();
            assertEquals(0, countInvalidStudyPlanTaskTypes(connection));
            businessTablesBeforeV17 = businessTableNames(connection);
            configKeysBeforeV17 = queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key");
            schemaBeforeV17 = schemaSignatureWithoutStudyPlanTaskV17Columns(connection);
            v1ToV16Checksums = appliedChecksums(v16Flyway, 16);
        }

        Flyway v17Flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("17"))
                .load();
        v17Flyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals("17", v17Flyway.info().current().getVersion().toString());
            assertEquals(v1ToV16Checksums, appliedChecksums(v17Flyway, 16));
            assertEquals(43, countBusinessTables(connection));
            assertEquals(31, queryInt(connection, "SELECT COUNT(*) FROM system_config"));
            assertEquals(businessTablesBeforeV17, businessTableNames(connection));
            assertEquals(configKeysBeforeV17,
                    queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key"));
            assertEquals(schemaBeforeV17, schemaSignatureWithoutStudyPlanTaskV17Columns(connection));
            assertStudyPlanTaskV17Definition(connection);
            assertSchemaFullStudyPlanTaskMatches(connection);
            assertV17MigrationCopiesMatch();
            businessTablesBeforeV18 = businessTableNames(connection);
            configKeysBeforeV18 = queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key");
            schemaBeforeV18 = schemaSignature(connection);
            v1ToV17Checksums = appliedChecksums(v17Flyway, 17);
        }

        Flyway v18Flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("18"))
                .load();
        v18Flyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals("18", v18Flyway.info().current().getVersion().toString());
            assertEquals(v1ToV17Checksums, appliedChecksums(v18Flyway, 17));
            assertEquals(44, countBusinessTables(connection));
            assertEquals(31, queryInt(connection, "SELECT COUNT(*) FROM system_config"));
            assertEquals(configKeysBeforeV18,
                    queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key"));
            List<String> businessTablesAfterV18 = businessTableNames(connection);
            assertEquals(businessTablesBeforeV18.size() + 1, businessTablesAfterV18.size());
            assertTrue(businessTablesAfterV18.containsAll(businessTablesBeforeV18));
            assertTrue(businessTablesAfterV18.contains("study_plan_action_history"));
            assertEquals(schemaBeforeV18, schemaSignatureWithoutStudyPlanActionHistory(connection));
            assertStudyPlanActionHistoryDefinition(connection);
            assertSchemaFullStudyPlanActionHistoryMatches(connection);
            assertV18MigrationCopiesMatch();
            businessTablesBeforeV19 = businessTableNames(connection);
            configKeysBeforeV19 = queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key");
            nonAiSchemaBeforeV19 = nonAiSchemaSignature(connection);
            v1ToV18Checksums = appliedChecksums(v18Flyway, 18);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertExecutedVersions(flyway);
            assertEquals("19", flyway.info().current().getVersion().toString());
            assertEquals(v1ToV18Checksums, appliedChecksums(flyway, 18));
            assertEquals(47, countBusinessTables(connection));
            assertEquals(31, queryInt(connection, "SELECT COUNT(*) FROM system_config"));
            assertEquals(configKeysBeforeV19,
                    queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key"));
            List<String> businessTablesAfterV19 = businessTableNames(connection);
            assertEquals(businessTablesBeforeV19.size() + 3, businessTablesAfterV19.size());
            assertTrue(businessTablesAfterV19.containsAll(businessTablesBeforeV19));
            assertTrue(businessTablesAfterV19.containsAll(List.of(
                    "ai_secret", "ai_extraction_confirmation", "ai_extraction_confirmation_item")));
            assertEquals(nonAiSchemaBeforeV19, nonAiSchemaSignature(connection));
            assertAiV19Definition(connection);
            assertSchemaFullAiV19Matches(connection);
            assertV19MigrationCopiesMatch();
        }
    }

    @Test
    void preservesExistingStudyLogWhenMigratingFromV14() throws SQLException {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(mysqlImage())
                .withDatabaseName("student_growth")
                .withUsername("student_growth")
                .withPassword("student_growth")) {
            mysql.start();

            Flyway v14Flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("14"))
                    .load();
            v14Flyway.migrate();

            Map<String, Integer> v1ToV14Checksums = appliedChecksums(v14Flyway, 14);
            List<String> schemaBeforeV15;
            List<String> businessTablesBeforeV15;
            List<String> configKeysBeforeV15;
            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                            INSERT INTO student(id, student_code, name)
                            VALUES (900001, 'V15-COMPAT', 'V15 Compatibility Student')
                            """);
                    statement.executeUpdate("""
                            INSERT INTO study_log(
                                id, student_id, subject_id, study_date, duration_seconds,
                                content, remark, deleted, create_time, update_time
                            ) VALUES (
                                900001, 900001, NULL, '2026-08-13', 3600,
                                'legacy content', 'legacy remark', 0,
                                '2026-08-13 08:00:00.000', '2026-08-13 09:00:00.000'
                            )
                            """);
                }
                assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM study_log WHERE id = 900001"));
                schemaBeforeV15 = schemaSignatureWithoutStudyLogVersion(connection);
                businessTablesBeforeV15 = businessTableNames(connection);
                configKeysBeforeV15 = queryStrings(connection,
                        "SELECT config_key FROM system_config ORDER BY config_key");
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("15"))
                    .load();
            flyway.migrate();

            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                assertEquals("15", flyway.info().current().getVersion().toString());
                assertEquals(v1ToV14Checksums, appliedChecksums(flyway, 14));
                assertEquals(42, countBusinessTables(connection));
                assertEquals(31, queryInt(connection, "SELECT COUNT(*) FROM system_config"));
                assertEquals(businessTablesBeforeV15, businessTableNames(connection));
                assertEquals(configKeysBeforeV15,
                        queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key"));
                assertEquals(schemaBeforeV15, schemaSignatureWithoutStudyLogVersion(connection));
                assertEquals(1, queryInt(connection, """
                        SELECT COUNT(*)
                        FROM study_log
                        WHERE id = 900001
                          AND student_id = 900001
                          AND subject_id IS NULL
                          AND study_date = '2026-08-13'
                          AND duration_seconds = 3600
                          AND content = 'legacy content'
                          AND remark = 'legacy remark'
                          AND deleted = 0
                          AND create_time = '2026-08-13 08:00:00.000'
                          AND update_time = '2026-08-13 09:00:00.000'
                          AND version = 0
                        """));
                assertEquals(0, queryInt(connection,
                        "SELECT COUNT(*) FROM study_log WHERE id = 900001 AND version IS NULL"));
                assertStudyLogVersionDefinition(connection);
            }
        }
    }

    @Test
    void createsStudentResourceAssignmentFromFrozenV15Baseline() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(mysqlImage())
                .withDatabaseName("student_growth")
                .withUsername("student_growth")
                .withPassword("student_growth")) {
            mysql.start();

            Flyway v15Flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("15"))
                    .load();
            v15Flyway.migrate();

            Map<String, Integer> v1ToV15Checksums = appliedChecksums(v15Flyway, 15);
            List<String> schemaBeforeV16;
            List<String> businessTablesBeforeV16;
            List<String> configKeysBeforeV16;
            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                assertEquals(42, countBusinessTables(connection));
                assertEquals(0, queryInt(connection, """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name = 'student_resource_assignment'
                        """));
                schemaBeforeV16 = schemaSignature(connection);
                businessTablesBeforeV16 = businessTableNames(connection);
                configKeysBeforeV16 = queryStrings(connection,
                        "SELECT config_key FROM system_config ORDER BY config_key");
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("16"))
                    .load();
            flyway.migrate();

            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                assertEquals("16", flyway.info().current().getVersion().toString());
                assertEquals(v1ToV15Checksums, appliedChecksums(flyway, 15));
                assertEquals(43, countBusinessTables(connection));
                assertEquals(31, queryInt(connection, "SELECT COUNT(*) FROM system_config"));
                assertEquals(configKeysBeforeV16,
                        queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key"));
                List<String> businessTablesAfterV16 = businessTableNames(connection);
                assertEquals(businessTablesBeforeV16.size() + 1, businessTablesAfterV16.size());
                assertTrue(businessTablesAfterV16.containsAll(businessTablesBeforeV16));
                assertEquals(schemaBeforeV16, schemaSignatureWithoutStudentResourceAssignment(connection));
                assertStudentResourceAssignmentDefinition(connection);
                assertSchemaFullStudentResourceAssignmentMatches(connection);
                assertV16MigrationCopiesMatch();
            }
        }
    }

    @Test
    void preservesExistingStudyPlanTaskWhenMigratingFromV16() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(mysqlImage())
                .withDatabaseName("student_growth")
                .withUsername("student_growth")
                .withPassword("student_growth")) {
            mysql.start();

            Flyway v16Flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("16"))
                    .load();
            v16Flyway.migrate();

            Map<String, Integer> v1ToV16Checksums = appliedChecksums(v16Flyway, 16);
            List<String> businessTablesBeforeV17;
            List<String> configKeysBeforeV17;
            List<String> schemaBeforeV17;
            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                            INSERT INTO student(id, student_code, name)
                            VALUES (900002, 'V17-COMPAT', 'V17 Compatibility Student')
                            """);
                    statement.executeUpdate("""
                            INSERT INTO study_plan(
                                id, student_id, title, plan_type, start_date, end_date,
                                status, daily_available_minutes, description, deleted, version,
                                create_time, update_time
                            ) VALUES (
                                900002, 900002, 'Legacy V16 plan', 'MANUAL',
                                '2026-08-01', '2026-08-31', 'DRAFT', 45,
                                'legacy plan', 0, 1,
                                '2026-08-16 08:00:00.000', '2026-08-16 08:00:00.000'
                            )
                            """);
                    statement.executeUpdate("""
                            INSERT INTO study_plan_task(
                                id, study_plan_id, task_date, task_type, title,
                                resource_id, wrong_question_id, knowledge_id,
                                expected_duration_seconds, status, completed_time,
                                sort_order, remark, create_time, update_time
                            ) VALUES (
                                900002, 900002, '2026-08-17', 'OTHER', 'Legacy V16 task',
                                NULL, NULL, NULL, 1800, 'TODO', NULL,
                                3, 'legacy task',
                                '2026-08-16 08:30:00.000', '2026-08-16 08:30:00.000'
                            )
                            """);
                }
                assertEquals(0, countInvalidStudyPlanTaskTypes(connection));
                businessTablesBeforeV17 = businessTableNames(connection);
                configKeysBeforeV17 = queryStrings(connection,
                        "SELECT config_key FROM system_config ORDER BY config_key");
                schemaBeforeV17 = schemaSignatureWithoutStudyPlanTaskV17Columns(connection);
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("17"))
                    .load();
            flyway.migrate();

            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                assertEquals("17", flyway.info().current().getVersion().toString());
                assertEquals(v1ToV16Checksums, appliedChecksums(flyway, 16));
                assertEquals(43, countBusinessTables(connection));
                assertEquals(31, queryInt(connection, "SELECT COUNT(*) FROM system_config"));
                assertEquals(businessTablesBeforeV17, businessTableNames(connection));
                assertEquals(configKeysBeforeV17,
                        queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key"));
                assertEquals(schemaBeforeV17, schemaSignatureWithoutStudyPlanTaskV17Columns(connection));
                assertEquals(1, queryInt(connection, """
                        SELECT COUNT(*)
                        FROM study_plan_task
                        WHERE id = 900002
                          AND study_plan_id = 900002
                          AND task_date = '2026-08-17'
                          AND task_type = 'OTHER'
                          AND title = 'Legacy V16 task'
                          AND resource_id IS NULL
                          AND wrong_question_id IS NULL
                          AND knowledge_id IS NULL
                          AND exam_id IS NULL
                          AND expected_duration_seconds = 1800
                          AND actual_duration_seconds IS NULL
                          AND status = 'TODO'
                          AND completed_time IS NULL
                          AND sort_order = 3
                          AND remark = 'legacy task'
                          AND version = 1
                          AND create_time = '2026-08-16 08:30:00.000'
                          AND update_time = '2026-08-16 08:30:00.000'
                        """));
                assertEquals(0, queryInt(connection,
                        "SELECT COUNT(*) FROM study_plan_task WHERE id = 900002 AND version IS NULL"));
                assertStudyPlanTaskV17Definition(connection);
                assertSchemaFullStudyPlanTaskMatches(connection);
                assertV17MigrationCopiesMatch();
            }
        }
    }

    @Test
    void preservesExistingAiDataWhenMigratingFromV18() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(mysqlImage())
                .withDatabaseName("student_growth")
                .withUsername("student_growth")
                .withPassword("student_growth")) {
            mysql.start();

            Flyway v18Flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("18"))
                    .load();
            v18Flyway.migrate();
            Map<String, Integer> v1ToV18Checksums = appliedChecksums(v18Flyway, 18);
            List<String> nonAiSchemaBeforeV19;

            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO student(id, student_code, name, current_stage_id, current_grade_id)
                        VALUES (900019, 'V19-AI-STUDENT', 'V19 AI Student', 1, 1)
                        """);
                statement.executeUpdate("""
                        INSERT INTO ai_model(
                            id, name, provider, model_code, api_base_url, api_key_ref,
                            supports_vision, supports_json, local_flag, enabled, priority_no, timeout_seconds)
                        VALUES (
                            900019, 'Legacy Vision Model', 'OPENAI', 'legacy-vision-model',
                            'https://ai.example.test/v1', NULL, 1, 1, 0, 1, 50, 90)
                        """);
                statement.executeUpdate("""
                        INSERT INTO ai_analysis(
                            id, student_id, business_type, business_id, ai_model_id, status,
                            input_summary, prompt_tokens, completion_tokens, duration_ms)
                        VALUES (900019, 900019, 'AI_BASELINE', 900019, 900019, 'PENDING',
                                'legacy input', 10, 5, 120)
                        """);
                statement.executeUpdate("""
                        INSERT INTO ai_extraction_task(
                            id, task_code, student_id, source_type, source_name, model_id,
                            status, input_type, recognize_analysis, match_knowledge, mask_personal_info)
                        VALUES (900019, 'V19-LEGACY-TASK', 900019, 'PRACTICE', 'legacy upload', 900019,
                                'REVIEW_REQUIRED', 'IMAGE', 1, 1, 1)
                        """);
                statement.executeUpdate("""
                        INSERT INTO ai_extraction_question(
                            id, task_id, sequence_no, question_text, status, user_modified)
                        VALUES (900019, 900019, 1, 'legacy temporary question', 'PENDING_REVIEW', 1)
                        """);
                nonAiSchemaBeforeV19 = nonAiSchemaSignature(connection);
                assertEquals(0, queryInt(connection,
                        "SELECT COUNT(*) FROM ai_model WHERE api_key_ref IS NOT NULL"));
            }

            Flyway v19Flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .load();
            v19Flyway.migrate();

            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                assertEquals("19", v19Flyway.info().current().getVersion().toString());
                assertEquals(v1ToV18Checksums, appliedChecksums(v19Flyway, 18));
                assertEquals(47, countBusinessTables(connection));
                assertEquals(31, queryInt(connection, "SELECT COUNT(*) FROM system_config"));
                assertEquals(nonAiSchemaBeforeV19, nonAiSchemaSignature(connection));
                assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM ai_model WHERE id = 900019"));
                assertEquals(1, queryInt(connection, """
                        SELECT COUNT(*) FROM ai_model
                        WHERE id = 900019
                          AND name = 'Legacy Vision Model'
                          AND provider = 'OPENAI'
                          AND model_name = 'legacy-vision-model'
                          AND api_base_url = 'https://ai.example.test/v1'
                          AND model_type = 'MULTIMODAL'
                          AND protocol = 'OPENAI_COMPATIBLE'
                          AND auth_type = 'BEARER_API_KEY'
                          AND version = 0
                        """));
                assertEquals(1, queryInt(connection, """
                        SELECT COUNT(*) FROM ai_analysis
                        WHERE id = 900019 AND input_summary = 'legacy input'
                          AND estimated_cost IS NULL AND currency_code IS NULL
                        """));
                assertEquals(1, queryInt(connection, """
                        SELECT COUNT(*) FROM ai_extraction_question
                        WHERE id = 900019 AND task_id = 900019
                          AND question_text = 'legacy temporary question' AND version = 0
                        """));
                assertAiV19Definition(connection);
            }
        }
    }

    private void assertExecutedVersions(Flyway flyway) {
        List<String> versions = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(Object::toString)
                .toList();

        assertEquals(
                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19"),
                versions);
    }

    private Map<String, Integer> appliedChecksums(Flyway flyway, int maximumVersion) {
        Map<String, Integer> checksums = new LinkedHashMap<>();
        Arrays.stream(flyway.info().applied())
                .filter(info -> info.getVersion() != null)
                .filter(info -> Integer.parseInt(info.getVersion().toString()) <= maximumVersion)
                .forEach(info -> checksums.put(info.getVersion().toString(), info.getChecksum()));
        return checksums;
    }

    private int countBusinessTables(Connection connection) throws SQLException {
        return queryInt(connection, """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """);
    }

    private List<String> businessTableNames(Connection connection) throws SQLException {
        return queryStrings(connection, """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """);
    }

    private void assertBasicData(Connection connection) throws SQLException {
        assertEquals(3, queryInt(connection, "SELECT COUNT(*) FROM stage"));
        assertEquals(12, queryInt(connection, "SELECT COUNT(*) FROM grade"));
        assertEquals(17, queryInt(connection, "SELECT COUNT(*) FROM subject"));
        assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM stage WHERE code = 'PRIMARY'"));
        assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM grade WHERE code = 'S3'"));
        assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM subject WHERE code = 'MATH'"));
    }

    private void assertDictionaryData(Connection connection) throws SQLException {
        assertEquals(5, queryInt(connection, "SELECT COUNT(*) FROM dict_type"));

        Map<String, Integer> expectedCounts = Map.of(
                "wrong_question_error_type", 10,
                "question_type", 14,
                "growth_event_type", 9,
                "learning_resource_source", 7);

        for (Map.Entry<String, Integer> entry : expectedCounts.entrySet()) {
            assertEquals(entry.getValue(), queryInt(connection, """
                    SELECT COUNT(*)
                    FROM dict_item di
                    JOIN dict_type dt ON dt.id = di.dict_type_id
                    WHERE dt.dict_code = ?
                    """, entry.getKey()));
        }
    }

    private void assertExistingAlgorithmConfig(Connection connection) throws SQLException {
        assertEquals(27, queryInt(connection, "SELECT COUNT(*) FROM system_config"));
        assertEquals(1, queryInt(connection,
                "SELECT COUNT(*) FROM system_config WHERE config_key = 'review.interval.days' AND config_value = '[1,3,7,15,30,60,120]'"));
        assertEquals(1, queryInt(connection,
                "SELECT COUNT(*) FROM system_config WHERE config_key = 'mastery.max_daily_decrease' AND config_value = '25'"));
    }

    private void assertAlgorithmConfig(Connection connection) throws SQLException {
        assertEquals(31, queryInt(connection, "SELECT COUNT(*) FROM system_config"));
        assertEquals(1, queryInt(connection,
                "SELECT COUNT(*) FROM system_config WHERE config_key = 'mastery.algorithm.version' AND config_value = '1.0'"));
        assertEquals(1, queryInt(connection,
                "SELECT COUNT(*) FROM system_config WHERE config_key = 'mastery.score_rate.correct_min' AND config_value = '0.80'"));
        assertEquals(1, queryInt(connection,
                "SELECT COUNT(*) FROM system_config WHERE config_key = 'mastery.score_rate.partial_min' AND config_value = '0.60'"));
        assertEquals(1, queryInt(connection,
                "SELECT COUNT(*) FROM system_config WHERE config_key = 'mastery.time_decay.enabled' AND config_value = 'false'"));
        assertEquals(0, queryInt(connection, """
                SELECT COUNT(*)
                FROM (
                    SELECT config_key
                    FROM system_config
                    GROUP BY config_key
                    HAVING COUNT(*) > 1
                ) duplicate_keys
                """));
    }

    private List<String> schemaSignature(Connection connection) throws SQLException {
        return queryStrings(connection, """
                SELECT CONCAT_WS('|', table_name, column_name, ordinal_position, column_type,
                                  is_nullable, COALESCE(column_default, '<NULL>'), column_key, extra)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name, ordinal_position
                """);
    }

    private List<String> nonAiSchemaSignature(Connection connection) throws SQLException {
        return queryStrings(connection, """
                SELECT CONCAT_WS('|', table_name, column_name, ordinal_position, column_type,
                                  is_nullable, COALESCE(column_default, '<NULL>'), column_key, extra)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name <> 'flyway_schema_history'
                  AND table_name NOT IN (
                      'ai_secret', 'ai_model', 'ai_analysis', 'ai_extraction_task',
                      'ai_extraction_question', 'ai_extraction_question_knowledge',
                      'ai_extraction_correction', 'ai_extraction_confirmation',
                      'ai_extraction_confirmation_item'
                  )
                ORDER BY table_name, ordinal_position
                """);
    }

    private List<String> schemaSignatureWithoutStudyLogVersion(Connection connection) throws SQLException {
        return queryStrings(connection, """
                SELECT CONCAT_WS('|', table_name, column_name, column_type,
                                  is_nullable, COALESCE(column_default, '<NULL>'), column_key, extra)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name <> 'flyway_schema_history'
                  AND NOT (table_name = 'study_log' AND column_name = 'version')
                ORDER BY table_name, column_name
                """);
    }

    private List<String> schemaSignatureWithoutStudentResourceAssignment(Connection connection) throws SQLException {
        return queryStrings(connection, """
                SELECT CONCAT_WS('|', table_name, column_name, ordinal_position, column_type,
                                  is_nullable, COALESCE(column_default, '<NULL>'), column_key, extra)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name <> 'flyway_schema_history'
                  AND table_name <> 'student_resource_assignment'
                ORDER BY table_name, ordinal_position
                """);
    }

    private List<String> schemaSignatureWithoutStudyPlanTaskV17Columns(Connection connection) throws SQLException {
        return queryStrings(connection, """
                SELECT CONCAT_WS('|', table_name, column_name, column_type,
                                  is_nullable, COALESCE(column_default, '<NULL>'), column_key, extra)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name <> 'flyway_schema_history'
                  AND NOT (
                      table_name = 'study_plan_task'
                      AND column_name IN ('exam_id', 'actual_duration_seconds', 'version')
                  )
                ORDER BY table_name, column_name
                """);
    }

    private List<String> schemaSignatureWithoutStudyPlanActionHistory(Connection connection) throws SQLException {
        return queryStrings(connection, """
                SELECT CONCAT_WS('|', table_name, column_name, ordinal_position, column_type,
                                  is_nullable, COALESCE(column_default, '<NULL>'), column_key, extra)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name <> 'flyway_schema_history'
                  AND table_name <> 'study_plan_action_history'
                ORDER BY table_name, ordinal_position
                """);
    }

    private List<String> studyLogColumnNames(Connection connection) throws SQLException {
        return queryStrings(connection, """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'study_log'
                ORDER BY ordinal_position
                """);
    }

    private void assertStudyLogVersionDefinition(Connection connection) throws SQLException {
        List<String> studyLogVersion = queryStrings(connection, """
                SELECT CONCAT_WS('|', data_type, is_nullable, column_default)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'study_log'
                  AND column_name = 'version'
                """);
        List<String> learningResourceVersion = queryStrings(connection, """
                SELECT CONCAT_WS('|', data_type, is_nullable, column_default)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'learning_resource'
                  AND column_name = 'version'
                """);

        assertEquals(List.of("int|NO|0"), studyLogVersion);
        assertEquals(learningResourceVersion, studyLogVersion);
    }

    private void assertSchemaFullStudyLogMatches(Connection connection) throws Exception {
        String ddl = Files.readAllLines(Path.of("database", "schema-full.sql")).stream()
                .filter(line -> line.startsWith("CREATE TABLE study_log("))
                .findFirst()
                .orElseThrow()
                .replace("CREATE TABLE study_log(", "CREATE TABLE study_log_schema_full_check(")
                .replace("CONSTRAINT fk_sl_student", "CONSTRAINT fk_sl_check_student")
                .replace("CONSTRAINT fk_sl_subject", "CONSTRAINT fk_sl_check_subject");
        String verificationTable = "study_log_schema_full_check";

        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + verificationTable);
            statement.execute(ddl);

            assertEquals(studyLogColumnSignature(connection, "study_log"),
                    studyLogColumnSignature(connection, verificationTable));

            statement.execute("DROP TABLE " + verificationTable);
        }
    }

    private void assertStudentResourceAssignmentDefinition(Connection connection) throws SQLException {
        assertEquals(List.of(
                "id|bigint|NO|<NULL>",
                "student_id|bigint|NO|<NULL>",
                "resource_id|bigint|NO|<NULL>",
                "status|varchar(32)|NO|WAITING",
                "assigned_time|datetime(3)|NO|CURRENT_TIMESTAMP(3)",
                "remark|varchar(512)|YES|<NULL>",
                "version|int|NO|0",
                "create_time|datetime(3)|NO|CURRENT_TIMESTAMP(3)",
                "update_time|datetime(3)|NO|CURRENT_TIMESTAMP(3)"),
                queryStrings(connection, """
                        SELECT CONCAT_WS('|', column_name, column_type, is_nullable,
                                         COALESCE(column_default, '<NULL>'))
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'student_resource_assignment'
                        ORDER BY ordinal_position
                        """));

        assertConstraint(connection, "student_resource_assignment", "uk_sra_student_resource", "UNIQUE");
        assertConstraint(connection, "student_resource_assignment", "fk_sra_student", "FOREIGN KEY");
        assertConstraint(connection, "student_resource_assignment", "fk_sra_resource", "FOREIGN KEY");
        assertConstraint(connection, "student_resource_assignment", "chk_sra_status", "CHECK");
        assertEquals(List.of("student_id|student", "resource_id|learning_resource"),
                queryStrings(connection, """
                        SELECT CONCAT_WS('|', column_name, referenced_table_name)
                        FROM information_schema.key_column_usage
                        WHERE constraint_schema = DATABASE()
                          AND table_name = 'student_resource_assignment'
                          AND referenced_table_name IS NOT NULL
                        ORDER BY CASE column_name WHEN 'student_id' THEN 1 WHEN 'resource_id' THEN 2 END
                        """));
        assertEquals(List.of("student_id", "resource_id"), queryStrings(connection, """
                SELECT column_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'student_resource_assignment'
                  AND index_name = 'uk_sra_student_resource'
                  AND non_unique = 0
                ORDER BY seq_in_index
                """));
        String statusCheck = queryStrings(connection, """
                SELECT check_clause
                FROM information_schema.check_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name = 'chk_sra_status'
                """).getFirst();
        for (String status : List.of("WAITING", "LEARNING", "COMPLETED", "REVIEW", "ARCHIVED")) {
            assertTrue(statusCheck.contains(status));
        }
    }

    private void assertSchemaFullStudentResourceAssignmentMatches(Connection connection) throws Exception {
        String ddl = Files.readAllLines(Path.of("database", "schema-full.sql")).stream()
                .filter(line -> line.startsWith("CREATE TABLE student_resource_assignment("))
                .findFirst()
                .orElseThrow()
                .replace("CREATE TABLE student_resource_assignment(",
                        "CREATE TABLE student_resource_assignment_schema_full_check(")
                .replace("CONSTRAINT fk_sra_student", "CONSTRAINT fk_sra_check_student")
                .replace("CONSTRAINT fk_sra_resource", "CONSTRAINT fk_sra_check_resource")
                .replace("CONSTRAINT uk_sra_student_resource", "CONSTRAINT uk_sra_check_student_resource")
                .replace("CONSTRAINT chk_sra_status", "CONSTRAINT chk_sra_check_status")
                .replace("INDEX idx_sra_student_status", "INDEX idx_sra_check_student_status");
        String verificationTable = "student_resource_assignment_schema_full_check";

        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + verificationTable);
            statement.execute(ddl);
            assertEquals(tableColumnSignature(connection, "student_resource_assignment"),
                    tableColumnSignature(connection, verificationTable));
            statement.execute("DROP TABLE " + verificationTable);
        }
    }

    private void assertV16MigrationCopiesMatch() throws Exception {
        assertArrayEquals(
                Files.readAllBytes(Path.of("database", "flyway", "V16__create_student_resource_assignment.sql")),
                Files.readAllBytes(Path.of("src", "main", "resources", "db", "migration",
                        "V16__create_student_resource_assignment.sql")));
    }

    private int countInvalidStudyPlanTaskTypes(Connection connection) throws SQLException {
        return queryInt(connection, """
                SELECT COUNT(*)
                FROM study_plan_task
                WHERE task_type NOT IN (
                    'WRONG_QUESTION_REVIEW',
                    'RESOURCE_LEARNING',
                    'KNOWLEDGE_PRACTICE',
                    'EXAM_REVIEW',
                    'READING',
                    'OTHER'
                )
                """);
    }

    private void assertStudyPlanTaskV17Definition(Connection connection) throws SQLException {
        assertEquals(List.of(
                "exam_id|bigint|YES|<NULL>",
                "actual_duration_seconds|int|YES|<NULL>",
                "version|int|NO|1"),
                queryStrings(connection, """
                        SELECT CONCAT_WS('|', column_name, column_type, is_nullable,
                                         COALESCE(column_default, '<NULL>'))
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'study_plan_task'
                          AND column_name IN ('exam_id', 'actual_duration_seconds', 'version')
                        ORDER BY FIELD(column_name, 'exam_id', 'actual_duration_seconds', 'version')
                        """));

        assertConstraint(connection, "study_plan_task", "fk_spt_exam", "FOREIGN KEY");
        assertConstraint(connection, "study_plan_task", "chk_spt_actual_duration", "CHECK");
        assertConstraint(connection, "study_plan_task", "chk_spt_task_type", "CHECK");
        assertEquals(List.of("exam_id|exam"), queryStrings(connection, """
                SELECT CONCAT_WS('|', column_name, referenced_table_name)
                FROM information_schema.key_column_usage
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'study_plan_task'
                  AND constraint_name = 'fk_spt_exam'
                """));

        String actualDurationCheck = queryStrings(connection, """
                SELECT check_clause
                FROM information_schema.check_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name = 'chk_spt_actual_duration'
                """).getFirst();
        assertTrue(actualDurationCheck.contains("actual_duration_seconds"));
        assertTrue(actualDurationCheck.contains(">= 0"));

        String taskTypeCheck = queryStrings(connection, """
                SELECT check_clause
                FROM information_schema.check_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name = 'chk_spt_task_type'
                """).getFirst();
        for (String taskType : List.of(
                "WRONG_QUESTION_REVIEW", "RESOURCE_LEARNING", "KNOWLEDGE_PRACTICE",
                "EXAM_REVIEW", "READING", "OTHER")) {
            assertTrue(taskTypeCheck.contains(taskType));
        }
    }

    private void assertSchemaFullStudyPlanTaskMatches(Connection connection) throws Exception {
        String ddl = Files.readAllLines(Path.of("database", "schema-full.sql")).stream()
                .filter(line -> line.startsWith("CREATE TABLE study_plan_task("))
                .findFirst()
                .orElseThrow()
                .replace("CREATE TABLE study_plan_task(",
                        "CREATE TABLE study_plan_task_schema_full_check(")
                .replace("CONSTRAINT fk_spt_plan", "CONSTRAINT fk_spt_check_plan")
                .replace("CONSTRAINT fk_spt_resource", "CONSTRAINT fk_spt_check_resource")
                .replace("CONSTRAINT fk_spt_wq", "CONSTRAINT fk_spt_check_wq")
                .replace("CONSTRAINT fk_spt_kn", "CONSTRAINT fk_spt_check_kn")
                .replace("CONSTRAINT fk_spt_exam", "CONSTRAINT fk_spt_check_exam");
        String verificationTable = "study_plan_task_schema_full_check";

        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + verificationTable);
            statement.execute(ddl);
            assertEquals(tableColumnSignature(connection, "study_plan_task"),
                    tableColumnSignature(connection, verificationTable));
            statement.execute("DROP TABLE " + verificationTable);
        }
    }

    private void assertV17MigrationCopiesMatch() throws Exception {
        assertArrayEquals(
                Files.readAllBytes(Path.of("database", "flyway",
                        "V17__complete_study_plan_task_contract.sql")),
                Files.readAllBytes(Path.of("src", "main", "resources", "db", "migration",
                        "V17__complete_study_plan_task_contract.sql")));
    }

    private void assertStudyPlanActionHistoryDefinition(Connection connection) throws SQLException {
        assertEquals(List.of(
                "id|bigint|NO|<NULL>",
                "study_plan_id|bigint|NO|<NULL>",
                "study_plan_task_id|bigint|YES|<NULL>",
                "action_type|varchar(32)|NO|<NULL>",
                "from_status|varchar(32)|NO|<NULL>",
                "to_status|varchar(32)|NO|<NULL>",
                "reason|varchar(512)|YES|<NULL>",
                "note|varchar(512)|YES|<NULL>",
                "version_before|int|NO|<NULL>",
                "version_after|int|NO|<NULL>",
                "create_time|datetime(3)|NO|CURRENT_TIMESTAMP(3)"),
                queryStrings(connection, """
                        SELECT CONCAT_WS('|', column_name, column_type, is_nullable,
                                         COALESCE(column_default, '<NULL>'))
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'study_plan_action_history'
                        ORDER BY ordinal_position
                        """));

        assertConstraint(connection, "study_plan_action_history", "fk_spah_plan", "FOREIGN KEY");
        assertConstraint(connection, "study_plan_action_history", "fk_spah_task", "FOREIGN KEY");
        assertConstraint(connection, "study_plan_action_history", "chk_spah_action_type", "CHECK");
        assertConstraint(connection, "study_plan_action_history", "chk_spah_task_scope", "CHECK");
        assertEquals(List.of("study_plan_id|study_plan", "study_plan_task_id|study_plan_task"),
                queryStrings(connection, """
                        SELECT CONCAT_WS('|', column_name, referenced_table_name)
                        FROM information_schema.key_column_usage
                        WHERE constraint_schema = DATABASE()
                          AND table_name = 'study_plan_action_history'
                          AND referenced_table_name IS NOT NULL
                        ORDER BY CASE column_name
                            WHEN 'study_plan_id' THEN 1
                            WHEN 'study_plan_task_id' THEN 2
                        END
                        """));

        String actionTypeCheck = queryStrings(connection, """
                SELECT check_clause
                FROM information_schema.check_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name = 'chk_spah_action_type'
                """).getFirst();
        for (String actionType : List.of(
                "PLAN_ACTIVATE", "PLAN_PAUSE", "PLAN_COMPLETE", "PLAN_CANCEL",
                "TASK_COMPLETE", "TASK_SKIP")) {
            assertTrue(actionTypeCheck.contains(actionType));
        }

        String taskScopeCheck = queryStrings(connection, """
                SELECT LOWER(check_clause)
                FROM information_schema.check_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name = 'chk_spah_task_scope'
                """).getFirst().replace("`", "");
        assertTrue(taskScopeCheck.contains("study_plan_task_id is null"));
        assertTrue(taskScopeCheck.contains("study_plan_task_id is not null"));
        assertTrue(taskScopeCheck.contains("plan_activate"));
        assertTrue(taskScopeCheck.contains("task_complete"));

        assertEquals(List.of(
                "idx_spah_plan_time|study_plan_id",
                "idx_spah_plan_time|create_time",
                "idx_spah_plan_time|id",
                "idx_spah_task_time|study_plan_task_id",
                "idx_spah_task_time|create_time",
                "idx_spah_task_time|id"),
                queryStrings(connection, """
                        SELECT CONCAT_WS('|', index_name, column_name)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'study_plan_action_history'
                          AND index_name IN ('idx_spah_plan_time', 'idx_spah_task_time')
                        ORDER BY CASE index_name
                            WHEN 'idx_spah_plan_time' THEN 1
                            WHEN 'idx_spah_task_time' THEN 2
                        END, seq_in_index
                        """));

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO student(id, student_code, name)
                    VALUES (900018, 'V18-CHECK', 'V18 Constraint Student')
                    """);
            statement.executeUpdate("""
                    INSERT INTO study_plan(
                        id, student_id, title, plan_type, start_date, end_date, status
                    ) VALUES (
                        900018, 900018, 'V18 Constraint Plan', 'MANUAL',
                        '2026-08-01', '2026-08-31', 'ACTIVE'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO study_plan_task(
                        id, study_plan_id, task_date, task_type, title, status
                    ) VALUES (
                        900018, 900018, '2026-08-17', 'OTHER', 'V18 Constraint Task', 'TODO'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO study_plan_action_history(
                        study_plan_id, study_plan_task_id, action_type,
                        from_status, to_status, version_before, version_after
                    ) VALUES (
                        900018, NULL, 'PLAN_PAUSE', 'ACTIVE', 'PAUSED', 1, 2
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO study_plan_action_history(
                        study_plan_id, study_plan_task_id, action_type,
                        from_status, to_status, reason, version_before, version_after
                    ) VALUES (
                        900018, 900018, 'TASK_SKIP', 'TODO', 'SKIPPED', 'not needed', 1, 2
                    )
                    """);

            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO study_plan_action_history(
                        study_plan_id, study_plan_task_id, action_type,
                        from_status, to_status, version_before, version_after
                    ) VALUES (
                        900018, 900018, 'PLAN_CANCEL', 'ACTIVE', 'CANCELLED', 1, 2
                    )
                    """));
            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO study_plan_action_history(
                        study_plan_id, study_plan_task_id, action_type,
                        from_status, to_status, version_before, version_after
                    ) VALUES (
                        900018, NULL, 'TASK_COMPLETE', 'TODO', 'COMPLETED', 1, 2
                    )
                    """));
        }
        assertEquals(2, queryInt(connection, "SELECT COUNT(*) FROM study_plan_action_history"));
    }

    private void assertSchemaFullStudyPlanActionHistoryMatches(Connection connection) throws Exception {
        String ddl = Files.readAllLines(Path.of("database", "schema-full.sql")).stream()
                .filter(line -> line.startsWith("CREATE TABLE study_plan_action_history("))
                .findFirst()
                .orElseThrow()
                .replace("CREATE TABLE study_plan_action_history(",
                        "CREATE TABLE study_plan_action_history_schema_full_check(")
                .replace("CONSTRAINT fk_spah_plan", "CONSTRAINT fk_spah_check_plan")
                .replace("CONSTRAINT fk_spah_task", "CONSTRAINT fk_spah_check_task")
                .replace("CONSTRAINT chk_spah_action_type", "CONSTRAINT chk_spah_check_action_type")
                .replace("CONSTRAINT chk_spah_task_scope", "CONSTRAINT chk_spah_check_task_scope")
                .replace("INDEX idx_spah_plan_time", "INDEX idx_spah_check_plan_time")
                .replace("INDEX idx_spah_task_time", "INDEX idx_spah_check_task_time");
        String verificationTable = "study_plan_action_history_schema_full_check";

        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + verificationTable);
            statement.execute(ddl);
            assertEquals(tableColumnSignature(connection, "study_plan_action_history"),
                    tableColumnSignature(connection, verificationTable));
            statement.execute("DROP TABLE " + verificationTable);
        }
    }

    private void assertV18MigrationCopiesMatch() throws Exception {
        assertArrayEquals(
                Files.readAllBytes(Path.of("database", "flyway",
                        "V18__create_study_plan_action_history.sql")),
                Files.readAllBytes(Path.of("src", "main", "resources", "db", "migration",
                        "V18__create_study_plan_action_history.sql")));
    }

    private void assertAiV19Definition(Connection connection) throws SQLException {
        assertEquals(List.of(
                "id|bigint|NO|<NULL>",
                "secret_ref|varchar(255)|NO|<NULL>",
                "encrypted_value|blob|NO|<NULL>",
                "nonce|varbinary(32)|NO|<NULL>",
                "algorithm|varchar(32)|NO|AES-256-GCM",
                "key_version|int|NO|1",
                "mask_suffix|varchar(16)|NO|<NULL>",
                "create_time|datetime(3)|NO|CURRENT_TIMESTAMP(3)",
                "update_time|datetime(3)|NO|CURRENT_TIMESTAMP(3)"),
                columnDefinitions(connection, "ai_secret"));

        assertEquals(List.of("int|NO|0"), columnDefinition(connection, "ai_model", "version"));
        assertEquals(columnDefinition(connection, "learning_resource", "version"),
                columnDefinition(connection, "ai_model", "version"));
        assertEquals(List.of("text|YES|<NULL>"), columnDefinition(connection, "ai_model", "remark"));
        assertEquals(List.of("varchar(512)|NO|<NULL>"), columnDefinition(connection, "ai_model", "api_base_url"));
        assertEquals(List.of("decimal(4,3)|YES|<NULL>"), columnDefinition(connection, "ai_model", "temperature"));
        assertEquals(List.of("int|YES|<NULL>"), columnDefinition(connection, "ai_model", "max_tokens"));
        assertEquals(List.of("int|NO|0"),
                columnDefinition(connection, "ai_extraction_question", "version"));
        assertEquals(List.of("decimal(18,6)|YES|<NULL>"),
                columnDefinition(connection, "ai_analysis", "estimated_cost"));
        assertEquals(List.of("char(3)|YES|<NULL>"),
                columnDefinition(connection, "ai_analysis", "currency_code"));

        for (String constraint : List.of(
                "chk_ai_model_provider", "chk_ai_model_type", "chk_ai_model_protocol",
                "chk_ai_model_auth_type", "chk_ai_model_temperature", "chk_ai_model_max_tokens")) {
            assertConstraint(connection, "ai_model", constraint, "CHECK");
        }
        assertCheckContains(connection, "chk_ai_model_provider",
                List.of("DOUBAO", "QWEN", "DEEPSEEK", "OPENAI", "OLLAMA", "CUSTOM"));
        assertCheckContains(connection, "chk_ai_model_type", List.of("CHAT", "MULTIMODAL", "EMBEDDING"));
        assertCheckContains(connection, "chk_ai_model_protocol", List.of("OPENAI_COMPATIBLE", "OLLAMA"));
        assertCheckContains(connection, "chk_ai_model_auth_type", List.of("NONE", "BEARER_API_KEY"));
        assertCheckContains(connection, "chk_ai_extraction_input_type", List.of("IMAGE", "PDF", "MIXED"));
        assertConstraint(connection, "ai_extraction_task", "chk_ai_extraction_input_type", "CHECK");

        assertConstraint(connection, "ai_model", "fk_ai_model_secret", "FOREIGN KEY");
        assertEquals(List.of("api_key_ref|ai_secret|secret_ref"), queryStrings(connection, """
                SELECT CONCAT_WS('|', column_name, referenced_table_name, referenced_column_name)
                FROM information_schema.key_column_usage
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'ai_model'
                  AND constraint_name = 'fk_ai_model_secret'
                ORDER BY ordinal_position
                """));

        assertUniqueIndexColumns(connection, "ai_extraction_confirmation",
                "uk_aic_task_idempotency", List.of("task_id", "idempotency_key"));
        assertUniqueIndexColumns(connection, "ai_extraction_confirmation_item",
                "uk_aici_question", List.of("question_id"));
        assertUniqueIndexColumns(connection, "ai_extraction_confirmation_item",
                "uk_aici_wrong_question", List.of("wrong_question_id"));
        assertUniqueIndexColumns(connection, "ai_extraction_question_knowledge",
                "uk_aeqk_question_knowledge", List.of("extraction_question_id", "knowledge_id"));
        assertUniqueIndexColumns(connection, "ai_extraction_question",
                "uk_ai_extraction_question_task_id", List.of("task_id", "id"));

        assertEquals(List.of("task_id|task_id", "question_id|id"), queryStrings(connection, """
                SELECT CONCAT_WS('|', column_name, referenced_column_name)
                FROM information_schema.key_column_usage
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'ai_extraction_correction'
                  AND constraint_name = 'fk_aec_task_question'
                ORDER BY ordinal_position
                """));
        assertConstraint(connection, "ai_analysis", "chk_ai_analysis_estimated_cost", "CHECK");
        assertConstraint(connection, "ai_analysis", "chk_ai_analysis_currency", "CHECK");
        assertConstraint(connection, "ai_extraction_confirmation", "chk_aic_status", "CHECK");
        assertCheckContains(connection, "chk_aic_status", List.of("PROCESSING", "COMPLETED"));
    }

    private void assertSchemaFullAiV19Matches(Connection connection) throws Exception {
        List<String> schemaLines = Files.readAllLines(Path.of("database", "schema-full.sql"));
        String aiModel = schemaLines.stream().filter(line -> line.startsWith("CREATE TABLE ai_model("))
                .findFirst().orElseThrow();
        String aiSecret = schemaLines.stream().filter(line -> line.startsWith("CREATE TABLE ai_secret("))
                .findFirst().orElseThrow();
        String question = schemaLines.stream()
                .filter(line -> line.startsWith("CREATE TABLE ai_extraction_question("))
                .findFirst().orElseThrow();
        String confirmation = schemaLines.stream()
                .filter(line -> line.startsWith("CREATE TABLE ai_extraction_confirmation("))
                .findFirst().orElseThrow();
        String confirmationItem = schemaLines.stream()
                .filter(line -> line.startsWith("CREATE TABLE ai_extraction_confirmation_item("))
                .findFirst().orElseThrow();
        String analysis = schemaLines.stream().filter(line -> line.startsWith("CREATE TABLE ai_analysis("))
                .findFirst().orElseThrow();

        for (String token : List.of("model_name", "model_type", "protocol", "auth_type", "api_base_url",
                "temperature", "max_tokens", "remark", "version", "fk_ai_model_secret")) {
            assertTrue(aiModel.contains(token));
        }
        assertFalse(aiModel.contains("model_code"));
        for (String token : List.of("encrypted_value", "nonce", "AES-256-GCM", "key_version", "mask_suffix")) {
            assertTrue(aiSecret.contains(token));
        }
        assertTrue(question.contains("version INT NOT NULL DEFAULT 0"));
        assertTrue(question.contains("uk_ai_extraction_question_task_id"));
        assertTrue(confirmation.contains("uk_aic_task_idempotency"));
        assertTrue(confirmationItem.contains("uk_aici_question"));
        assertTrue(confirmationItem.contains("uk_aici_wrong_question"));
        assertTrue(analysis.contains("estimated_cost DECIMAL(18,6)"));
        assertTrue(analysis.contains("currency_code CHAR(3)"));
        assertEquals(47, countBusinessTables(connection));
    }

    private List<String> columnDefinitions(Connection connection, String tableName) throws SQLException {
        return queryStrings(connection, """
                SELECT CONCAT_WS('|', column_name, column_type, is_nullable,
                                 COALESCE(column_default, '<NULL>'))
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ?
                ORDER BY ordinal_position
                """, tableName);
    }

    private List<String> columnDefinition(Connection connection, String tableName, String columnName)
            throws SQLException {
        return queryStrings(connection, """
                SELECT CONCAT_WS('|', column_type, is_nullable, COALESCE(column_default, '<NULL>'))
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, tableName, columnName);
    }

    private void assertCheckContains(Connection connection, String constraintName, List<String> values)
            throws SQLException {
        String clause = queryStrings(connection, """
                SELECT check_clause
                FROM information_schema.check_constraints
                WHERE constraint_schema = DATABASE() AND constraint_name = ?
                """, constraintName).getFirst();
        for (String value : values) assertTrue(clause.contains(value));
    }

    private void assertUniqueIndexColumns(Connection connection, String tableName, String indexName,
            List<String> expectedColumns) throws SQLException {
        assertEquals(expectedColumns, queryStrings(connection, """
                SELECT column_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? AND non_unique = 0
                ORDER BY seq_in_index
                """, tableName, indexName));
    }

    private void assertV19MigrationCopiesMatch() throws Exception {
        assertArrayEquals(
                Files.readAllBytes(Path.of("database", "flyway", "V19__complete_ai_foundation.sql")),
                Files.readAllBytes(Path.of("src", "main", "resources", "db", "migration",
                        "V19__complete_ai_foundation.sql")));
    }

    private List<String> studyLogColumnSignature(Connection connection, String tableName) throws SQLException {
        return tableColumnSignature(connection, tableName);
    }

    private List<String> tableColumnSignature(Connection connection, String tableName) throws SQLException {
        return queryStrings(connection, """
                SELECT CONCAT_WS('|', column_name, ordinal_position, column_type,
                                  is_nullable, COALESCE(column_default, '<NULL>'), extra)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                ORDER BY ordinal_position
                """, tableName);
    }

    private void assertConstraints(Connection connection) throws SQLException {
        assertConstraint(connection, "grade", "fk_grade_stage", "FOREIGN KEY");
        assertConstraint(connection, "student_mastery", "uk_sm", "UNIQUE");
        assertConstraint(connection, "wrong_question", "chk_wq_status_v3", "CHECK");
        assertConstraint(connection, "ai_extraction_task", "chk_aet_status_v3", "CHECK");
        assertConstraint(connection, "ai_extraction_question", "chk_aeq_status_v3", "CHECK");
        assertTableHasCheckConstraint(connection, "learning_resource");
        assertTableHasCheckConstraint(connection, "resource_history");

        assertTrue(queryInt(connection, """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'wrong_question'
                  AND index_name = 'idx_wq_filter'
                """) > 0);
    }

    private void assertConstraint(Connection connection, String tableName, String constraintName, String constraintType)
            throws SQLException {
        assertEquals(1, queryInt(connection, """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = ?
                  AND constraint_name = ?
                  AND constraint_type = ?
                """, tableName, constraintName, constraintType));
    }

    private void assertTableHasCheckConstraint(Connection connection, String tableName) throws SQLException {
        assertTrue(queryInt(connection, """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = ?
                  AND constraint_type = 'CHECK'
                """, tableName) > 0);
    }

    private int queryInt(Connection connection, String sql, String... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setString(i + 1, params[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        }
    }

    private List<String> queryStrings(Connection connection, String sql, String... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setString(i + 1, params[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
            List<String> values = new java.util.ArrayList<>();
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
            return values;
            }
        }
    }
}
