package com.stdntedu.transfer.exporttask;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.generated.model.ExportFormat;
import com.stdntedu.transfer.exporttask.ExportDatasetCatalog.Spec;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

@Component
public class ExportArtifactWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ExportArtifactWriter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Artifact write(Long taskId, ExportFormat format, List<Spec> specs) throws IOException {
        return switch (format) {
            case CSV -> csv(taskId, specs.getFirst());
            case JSON -> json(taskId, specs);
            case XLSX -> xlsx(taskId, specs);
        };
    }

    private Artifact csv(Long id, Spec spec) throws IOException {
        Path path = Files.createTempFile("export-" + id + "-", ".csv");
        try (Writer output = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
                CSVPrinter csv = new CSVPrinter(output, CSVFormat.RFC4180.builder()
                        .setHeader(spec.columns().toArray(String[]::new)).get())) {
            stream(spec, values -> {
                try { csv.printRecord(values.stream().map(this::csvValue).toList()); }
                catch (IOException ex) { throw new WriteFailure(ex); }
            });
        } catch (WriteFailure ex) {
            Files.deleteIfExists(path);
            throw ex.io;
        }
        return new Artifact(path, "export-" + id + ".csv", "text/csv");
    }

    private Artifact json(Long id, List<Spec> specs) throws IOException {
        Path path = Files.createTempFile("export-" + id + "-", ".json");
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(path));
                JsonGenerator generator = json.getFactory().createGenerator(output)) {
            generator.writeStartObject();
            for (Spec spec : specs) {
                generator.writeFieldName(spec.type().getValue());
                generator.writeStartArray();
                stream(spec, values -> {
                    try {
                        generator.writeStartObject();
                        for (int i = 0; i < spec.columns().size(); i++) {
                            generator.writeFieldName(spec.columns().get(i));
                            generator.writeObject(normalize(values.get(i)));
                        }
                        generator.writeEndObject();
                    } catch (IOException ex) { throw new WriteFailure(ex); }
                });
                generator.writeEndArray();
            }
            generator.writeEndObject();
        } catch (WriteFailure ex) {
            Files.deleteIfExists(path);
            throw ex.io;
        }
        return new Artifact(path, "export-" + id + ".json", "application/json");
    }

    private Artifact xlsx(Long id, List<Spec> specs) throws IOException {
        Path path = Files.createTempFile("export-" + id + "-", ".xlsx");
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            for (Spec spec : specs) {
                Sheet sheet = workbook.createSheet(spec.type().getValue());
                Row header = sheet.createRow(0);
                for (int i = 0; i < spec.columns().size(); i++) header.createCell(i).setCellValue(spec.columns().get(i));
                int[] rowNumber = {1};
                stream(spec, values -> {
                    Row row = sheet.createRow(rowNumber[0]++);
                    for (int i = 0; i < values.size(); i++) writeCell(row.createCell(i), values.get(i));
                });
            }
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(path))) {
                workbook.write(output);
            }
            workbook.dispose();
        } catch (RuntimeException | IOException ex) {
            Files.deleteIfExists(path);
            throw ex;
        }
        return new Artifact(path, "export-" + id + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    private void stream(Spec spec, Consumer<List<Object>> consumer) {
        jdbc.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(spec.sql(),
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            statement.setFetchSize(1_000);
            for (int i = 0; i < spec.args().size(); i++) statement.setObject(i + 1, spec.args().get(i));
            return statement;
        }, (RowCallbackHandler) row -> consumer.accept(values(row, spec.columns().size())));
    }

    private List<Object> values(ResultSet row, int size) throws SQLException {
        java.util.ArrayList<Object> values = new java.util.ArrayList<>(size);
        for (int i = 1; i <= size; i++) values.add(row.getObject(i));
        return values;
    }

    private Object normalize(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toString();
        if (value instanceof java.sql.Date date) return date.toLocalDate().toString();
        if (value instanceof byte[] bytes) return java.util.HexFormat.of().formatHex(bytes);
        return value;
    }

    private String csvValue(Object value) {
        if (value == null) return "";
        String text = String.valueOf(normalize(value));
        return dangerousFormula(text) ? "'" + text : text;
    }

    private void writeCell(Cell cell, Object value) {
        Object normalized = normalize(value);
        if (normalized == null) return;
        if (normalized instanceof Number number && !(normalized instanceof BigDecimal)) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        String text = String.valueOf(normalized);
        cell.setCellValue(dangerousFormula(text) ? "'" + text : text);
    }

    private boolean dangerousFormula(String text) {
        return !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0;
    }

    public record Artifact(Path path, String fileName, String mimeType) { }
    private static final class WriteFailure extends RuntimeException {
        private final IOException io;
        private WriteFailure(IOException io) { super(io); this.io = io; }
    }
}
