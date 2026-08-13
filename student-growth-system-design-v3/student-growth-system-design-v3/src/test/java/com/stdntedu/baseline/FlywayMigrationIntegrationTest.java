package com.stdntedu.baseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
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
    void migratesEmptyDatabaseAndValidatesBaseline() throws SQLException {
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

        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertExecutedVersions(flyway);
            assertEquals("14", flyway.info().current().getVersion().toString());
            assertEquals(42, countBusinessTables(connection));
            assertBasicData(connection);
            assertDictionaryData(connection);
            assertAlgorithmConfig(connection);
            assertTrue(queryStrings(connection, "SELECT config_key FROM system_config ORDER BY config_key")
                    .containsAll(originalConfigKeys));
            assertEquals(schemaBeforeV14, schemaSignature(connection));
            assertConstraints(connection);
        }
    }

    private void assertExecutedVersions(Flyway flyway) {
        List<String> versions = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(Object::toString)
                .toList();

        assertEquals(
                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14"),
                versions);
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

    private List<String> queryStrings(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            List<String> values = new java.util.ArrayList<>();
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
            return values;
        }
    }
}
