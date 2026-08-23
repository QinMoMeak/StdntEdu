package com.stdntedu.backup.packageformat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.backup.packageformat.BackupManifest.Entry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LogicalBackupDataService {
    private static final String TERMINAL_EXTRACTION = "t.status NOT IN ('PENDING','RUNNING')";
    private static final List<Dataset> BASE = List.of(
            dataset("dict_item", "SELECT * FROM dict_item WHERE system_flag=0 ORDER BY id"),
            dataset("student"), dataset("academic_term"), dataset("knowledge_node"),
            dataset("knowledge_relation"), dataset("exam"), dataset("score_record"), dataset("score_knowledge"),
            dataset("wrong_question"), dataset("wrong_question_knowledge"), dataset("wrong_review"),
            dataset("student_mastery"), dataset("mastery_history"), dataset("learning_resource"),
            dataset("learning_resource_knowledge"), dataset("student_resource_assignment"),
            dataset("resource_history"), dataset("study_log"), dataset("growth_event"), dataset("ai_model"),
            dataset("ai_analysis", "SELECT * FROM ai_analysis WHERE status NOT IN ('PENDING','RUNNING') ORDER BY id"),
            dataset("study_plan"), dataset("study_plan_task"), dataset("study_plan_action_history"),
            dataset("recommendation"), dataset("growth_report"));
    private static final List<Dataset> ATTACHMENT_DATA = List.of(
            dataset("attachment", """
                    SELECT a.* FROM attachment a
                    WHERE NOT EXISTS (SELECT 1 FROM import_task i WHERE i.attachment_id=a.id OR i.error_report_attachment_id=a.id)
                      AND NOT EXISTS (SELECT 1 FROM export_task e WHERE e.output_attachment_id=a.id)
                       OR EXISTS (SELECT 1 FROM entity_attachment ea WHERE ea.attachment_id=a.id)
                       OR EXISTS (SELECT 1 FROM ai_extraction_file f JOIN ai_extraction_task t ON t.id=f.task_id
                                  WHERE f.attachment_id=a.id AND t.status NOT IN ('PENDING','RUNNING'))
                    ORDER BY a.id
                    """, Set.of("storage_path")),
            dataset("ai_extraction_task", "SELECT * FROM ai_extraction_task t WHERE " + TERMINAL_EXTRACTION + " ORDER BY t.id"),
            dataset("ai_extraction_file", "SELECT f.* FROM ai_extraction_file f JOIN ai_extraction_task t ON t.id=f.task_id WHERE " + TERMINAL_EXTRACTION + " ORDER BY f.id"),
            dataset("ai_extraction_question", "SELECT q.* FROM ai_extraction_question q JOIN ai_extraction_task t ON t.id=q.task_id WHERE " + TERMINAL_EXTRACTION + " ORDER BY q.id"),
            dataset("ai_extraction_question_knowledge", "SELECT k.* FROM ai_extraction_question_knowledge k JOIN ai_extraction_question q ON q.id=k.extraction_question_id JOIN ai_extraction_task t ON t.id=q.task_id WHERE " + TERMINAL_EXTRACTION + " ORDER BY k.id"),
            dataset("ai_extraction_correction", "SELECT c.* FROM ai_extraction_correction c JOIN ai_extraction_task t ON t.id=c.task_id WHERE " + TERMINAL_EXTRACTION + " ORDER BY c.id"),
            dataset("ai_extraction_confirmation", "SELECT c.* FROM ai_extraction_confirmation c JOIN ai_extraction_task t ON t.id=c.task_id WHERE " + TERMINAL_EXTRACTION + " AND c.status='COMPLETED' ORDER BY c.id"),
            dataset("ai_extraction_confirmation_item", "SELECT i.* FROM ai_extraction_confirmation_item i JOIN ai_extraction_confirmation c ON c.id=i.confirmation_id JOIN ai_extraction_task t ON t.id=c.task_id WHERE " + TERMINAL_EXTRACTION + " AND c.status='COMPLETED' ORDER BY i.id"),
            dataset("entity_attachment"));

    private static final List<String> DELETE_ORDER = List.of(
            "operation_log", "import_task", "export_task", "entity_attachment",
            "study_plan_action_history", "study_plan_task", "study_plan", "growth_report", "recommendation",
            "ai_extraction_confirmation_item", "ai_extraction_confirmation", "ai_extraction_correction",
            "ai_extraction_question_knowledge", "ai_extraction_question", "ai_extraction_file",
            "ai_extraction_task", "score_knowledge", "wrong_question_knowledge", "wrong_review",
            "student_mastery", "mastery_history", "resource_history", "student_resource_assignment",
            "learning_resource_knowledge", "score_record", "wrong_question", "exam", "study_log",
            "growth_event", "learning_resource", "ai_analysis", "ai_model", "ai_secret", "attachment",
            "knowledge_relation", "knowledge_node", "academic_term", "student");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate snapshot;
    private final TransactionTemplate restore;

    public LogicalBackupDataService(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.json = json.copy();
        this.snapshot = new TransactionTemplate(transactions);
        this.snapshot.setReadOnly(true);
        this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.restore = new TransactionTemplate(transactions);
    }

    public Snapshot createSnapshot(Path dataDirectory, boolean includeAttachments, boolean includeSecrets) {
        return snapshot.execute(status -> {
            try {
                Files.createDirectories(dataDirectory);
                List<Dataset> datasets = new ArrayList<>(BASE);
                if (includeSecrets) datasets.add(datasets.indexOf(datasets.stream()
                        .filter(value -> "ai_model".equals(value.table())).findFirst().orElseThrow()), dataset("ai_secret"));
                if (includeAttachments) datasets.addAll(ATTACHMENT_DATA);
                List<DataFile> files = new ArrayList<>();
                long records = 0;
                for (Dataset dataset : datasets) {
                    DataFile file = writeDataset(dataDirectory, dataset, includeSecrets);
                    files.add(file);
                    records += file.entry().recordCount();
                }
                List<AttachmentSource> attachments = includeAttachments ? attachmentSources() : List.of();
                String databaseVersion = jdbc.queryForObject(
                        "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1",
                        String.class);
                return new Snapshot(List.copyOf(files), List.copyOf(attachments), records, databaseVersion);
            } catch (IOException ex) {
                throw new IllegalStateException("backup snapshot could not be written", ex);
            }
        });
    }

    private DataFile writeDataset(Path directory, Dataset dataset, boolean includeSecrets) throws IOException {
        Path file = directory.resolve(dataset.table() + ".json");
        long[] records = {0};
        try (JsonGenerator generator = json.getFactory().createGenerator(Files.newOutputStream(file))) {
            try {
                jdbc.query(dataset.sql(), (ResultSet result) -> {
                    try {
                        ResultSetMetaData metadata = result.getMetaData();
                        List<Column> columns = columns(metadata, dataset.excludedColumns());
                        generator.writeStartObject();
                        generator.writeStringField("table", dataset.table());
                        generator.writeObjectField("columns", columns);
                        generator.writeArrayFieldStart("rows");
                        while (result.next()) {
                            generator.writeStartObject();
                            for (Column column : columns) {
                                Object value = result.getObject(column.name());
                                if ("ai_model".equals(dataset.table()) && "api_key_ref".equals(column.name())
                                        && !includeSecrets) value = null;
                                generator.writeFieldName(column.name());
                                writeValue(generator, result, column, value);
                            }
                            generator.writeEndObject();
                            records[0]++;
                        }
                        generator.writeEndArray();
                        generator.writeEndObject();
                        return null;
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                });
            } catch (UncheckedIOException ex) { throw ex.getCause(); }
        }
        return new DataFile(file, new Entry("data/" + file.getFileName(), dataset.table(), null, records[0],
                Files.size(file), sha256(file)));
    }

    private List<Column> columns(ResultSetMetaData metadata, Set<String> excluded) throws SQLException {
        List<Column> columns = new ArrayList<>();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            String name = metadata.getColumnLabel(i);
            if (!excluded.contains(name)) columns.add(new Column(name, metadata.getColumnType(i),
                    metadata.getColumnTypeName(i)));
        }
        return columns;
    }

    private void writeValue(JsonGenerator generator, ResultSet result, Column column, Object value) throws IOException, SQLException {
        if (value == null) { generator.writeNull(); return; }
        switch (column.jdbcType()) {
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB ->
                    generator.writeString(Base64.getEncoder().encodeToString(result.getBytes(column.name())));
            case Types.DATE -> generator.writeString(result.getDate(column.name()).toLocalDate().toString());
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
                    generator.writeString(result.getTimestamp(column.name()).toLocalDateTime().toString());
            default -> generator.writeObject(value);
        }
    }

    private List<AttachmentSource> attachmentSources() {
        String query = ATTACHMENT_DATA.getFirst().sql().replace("SELECT a.*", "SELECT a.id,a.file_name,a.storage_path,a.mime_type,a.file_size,a.sha256");
        return jdbc.query(query, (rs, row) -> new AttachmentSource(rs.getLong("id"), rs.getString("file_name"),
                Path.of(rs.getString("storage_path")), rs.getString("mime_type"), rs.getLong("file_size"),
                rs.getString("sha256")));
    }

    public RestoreResult restore(Path stagedRoot, BackupManifest manifest, Map<Long, String> attachmentPaths,
            boolean restoreAttachments, boolean restoreSecrets, Runnable beforeCommit) {
        return restore.execute(status -> {
            for (String table : DELETE_ORDER) {
                jdbc.update("DELETE FROM `" + table + "`");
            }
            jdbc.update("DELETE FROM dict_item WHERE system_flag=0");
            int tables = 0;
            long rows = 0;
            Map<Long, Long> knowledgeParents = new LinkedHashMap<>();
            for (Entry entry : manifest.datasets()) {
                String table = entry.table();
                if (("ai_secret".equals(table) && !restoreSecrets)
                        || (isAttachmentDomain(table) && !restoreAttachments)) continue;
                long inserted = insertDataset(stagedRoot.resolve(entry.path()), table, attachmentPaths,
                        restoreSecrets, knowledgeParents);
                tables++;
                rows += inserted;
            }
            for (Map.Entry<Long, Long> parent : knowledgeParents.entrySet()) {
                jdbc.update("UPDATE knowledge_node SET parent_id=? WHERE id=?", parent.getValue(), parent.getKey());
            }
            beforeCommit.run();
            return new RestoreResult(tables, rows);
        });
    }

    private long insertDataset(Path file, String table, Map<Long, String> attachmentPaths,
            boolean restoreSecrets, Map<Long, Long> knowledgeParents) {
        try {
            JsonNode root = json.readTree(file.toFile());
            List<Column> columns = new ArrayList<>();
            for (JsonNode column : root.path("columns")) columns.add(json.treeToValue(column, Column.class));
            if ("attachment".equals(table)) columns.add(new Column("storage_path", Types.VARCHAR, "VARCHAR"));
            String sql = "INSERT INTO `" + table + "`(" + columns.stream().map(c -> "`" + c.name() + "`")
                    .collect(java.util.stream.Collectors.joining(",")) + ") VALUES(" +
                    String.join(",", java.util.Collections.nCopies(columns.size(), "?")) + ")";
            long count = 0;
            for (JsonNode row : root.path("rows")) {
                Long rowId = row.hasNonNull("id") ? row.path("id").longValue() : null;
                if ("knowledge_node".equals(table) && row.hasNonNull("parent_id")) {
                    knowledgeParents.put(rowId, row.path("parent_id").longValue());
                }
                PreparedStatementSetter setter = statement -> {
                    for (int i = 0; i < columns.size(); i++) {
                        Column column = columns.get(i);
                        JsonNode value = row.get(column.name());
                        if ("attachment".equals(table) && "storage_path".equals(column.name())) {
                            statement.setString(i + 1, attachmentPaths.get(rowId));
                        } else if ("knowledge_node".equals(table) && "parent_id".equals(column.name())) {
                            statement.setNull(i + 1, Types.BIGINT);
                        } else if ("ai_model".equals(table) && "api_key_ref".equals(column.name()) && !restoreSecrets) {
                            statement.setNull(i + 1, Types.VARCHAR);
                        } else {
                            bind(statement, i + 1, column, value);
                        }
                    }
                };
                jdbc.update(sql, setter);
                count++;
            }
            return count;
        } catch (IOException ex) {
            throw new IllegalStateException("backup dataset could not be restored", ex);
        }
    }

    private void bind(java.sql.PreparedStatement statement, int index, Column column, JsonNode value) throws SQLException {
        if (value == null || value.isNull()) { statement.setNull(index, column.jdbcType()); return; }
        switch (column.jdbcType()) {
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB ->
                    statement.setBytes(index, Base64.getDecoder().decode(value.asText()));
            case Types.DATE -> statement.setDate(index, Date.valueOf(LocalDate.parse(value.asText())));
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
                    statement.setTimestamp(index, Timestamp.valueOf(LocalDateTime.parse(value.asText())));
            case Types.BIGINT -> statement.setLong(index, value.longValue());
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> statement.setInt(index, value.intValue());
            case Types.DECIMAL, Types.NUMERIC -> statement.setBigDecimal(index, value.decimalValue());
            case Types.BOOLEAN, Types.BIT -> statement.setBoolean(index, value.asBoolean());
            default -> statement.setString(index, value.isTextual() ? value.textValue() : value.toString());
        }
    }

    private boolean isAttachmentDomain(String table) {
        return "attachment".equals(table) || "entity_attachment".equals(table)
                || table.startsWith("ai_extraction_");
    }

    private static Dataset dataset(String table) { return dataset(table, "SELECT * FROM `" + table + "` ORDER BY 1"); }
    private static Dataset dataset(String table, String sql) { return dataset(table, sql, Set.of()); }
    private static Dataset dataset(String table, String sql, Set<String> excluded) { return new Dataset(table, sql, excluded); }

    static String sha256(Path path) {
        try (var input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 failed", ex); }
    }

    public record Snapshot(List<DataFile> datasets, List<AttachmentSource> attachments, long recordCount,
            String databaseVersion) { }

    public String currentDatabaseVersion() {
        return jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1",
                String.class);
    }

    public record DataFile(Path path, Entry entry) { }
    public record AttachmentSource(Long id, String fileName, Path path, String mimeType, long size, String sha256) { }
    public record RestoreResult(int tableCount, long rowCount) { }
    public record Column(String name, int jdbcType, String typeName) { }
    private record Dataset(String table, String sql, Set<String> excludedColumns) { }
}
