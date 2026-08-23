package com.stdntedu.transfer.importtask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.file.ZipArchiveSafety;
import com.stdntedu.generated.model.ExamCreate;
import com.stdntedu.generated.model.ImportType;
import com.stdntedu.generated.model.KnowledgeNodeCreateRequest;
import com.stdntedu.generated.model.ResourceCreate;
import com.stdntedu.generated.model.StudentCreate;
import com.stdntedu.generated.model.WrongCreate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ImportFileParser {
    static final int MAX_ENTRIES = 20;
    static final int MAX_ROWS = 10_000;
    static final int MAX_COLUMNS = 100;
    static final int MAX_SHEETS = 20;
    static final int MAX_STRING = 65_536;
    static final long MAX_ENTRY_BYTES = 50L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 200L * 1024 * 1024;
    static final double MAX_RATIO = 100.0;

    private final ObjectMapper json;
    private final Validator validator;

    public ImportFileParser(ObjectMapper objectMapper, Validator validator) {
        this.json = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.json.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(20).maxStringLength(MAX_STRING).build());
        this.validator = validator;
    }

    public ParsedImport parse(Path path, String fileName, ImportType type) {
        String suffix = suffix(fileName);
        if ((type == ImportType.SCORE || type == ImportType.WRONG_QUESTION)
                && !Set.of(".json", ".zip").contains(suffix)) {
            throw invalid("SCORE and WRONG_QUESTION imports require JSON or ZIP");
        }
        List<ParsedRow> rows = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();
        try {
            int files = ".zip".equals(suffix)
                    ? parseZip(path, type, rows, errors)
                    : parseOne(Files.newInputStream(path), fileName, null, suffix, type, rows, errors);
            if (rows.size() + errors.size() > MAX_ROWS) throw invalid("import exceeds row limit");
            return new ParsedImport(List.copyOf(rows), List.copyOf(errors), files);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid("import file could not be parsed");
        }
    }

    private int parseZip(Path path, ImportType type, List<ParsedRow> rows, List<ImportError> errors) throws Exception {
        int count = 0;
        long total = 0;
        try (ZipFile zip = ZipFile.builder().setPath(path).get()) {
            var entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                if (++count > MAX_ENTRIES) throw invalid("ZIP exceeds entry limit");
                String name;
                try { name = ZipArchiveSafety.safeEntryName(entry, MAX_ENTRY_BYTES, MAX_RATIO); }
                catch (IllegalArgumentException ex) { throw invalid(ex.getMessage()); }
                String suffix = suffix(name);
                if (".zip".equals(suffix)) throw invalid("nested ZIP is not supported");
                if (!Set.of(".csv", ".xlsx", ".json").contains(suffix)) {
                    throw invalid("ZIP contains an unsupported file type");
                }
                if ((type == ImportType.SCORE || type == ImportType.WRONG_QUESTION)
                        && !".json".equals(suffix)) throw invalid("nested data import requires JSON");
                byte[] bytes;
                try (InputStream input = zip.getInputStream(entry)) {
                    bytes = readBounded(input, MAX_ENTRY_BYTES);
                }
                total += bytes.length;
                if (total > MAX_TOTAL_BYTES) throw invalid("ZIP expanded data exceeds limit");
                parseOne(new ByteArrayInputStream(bytes), name, null, suffix, type, rows, errors);
            }
        }
        if (count == 0) throw invalid("ZIP contains no import files");
        return count;
    }

    private int parseOne(InputStream input, String file, String sheet, String suffix, ImportType type,
            List<ParsedRow> rows, List<ImportError> errors) throws Exception {
        try (input) {
            return switch (suffix) {
                case ".csv" -> parseCsv(input, file, type, rows, errors);
                case ".xlsx" -> parseXlsx(input, file, type, rows, errors);
                case ".json" -> parseJson(input, file, sheet, type, rows, errors);
                default -> throw invalid("file type is not supported");
            };
        }
    }

    private int parseCsv(InputStream input, String file, ImportType type, List<ParsedRow> rows,
            List<ImportError> errors) throws IOException {
        try (Reader reader = new InputStreamReader(withoutUtf8Bom(input), StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get()
                        .parse(reader)) {
            List<String> headers = parser.getHeaderNames();
            validateHeaders(headers);
            for (CSVRecord record : parser) {
                if (record.getRecordNumber() > MAX_ROWS) throw invalid("CSV exceeds row limit");
                Map<String, String> values = new LinkedHashMap<>();
                boolean empty = true;
                for (String header : headers) {
                    String value = record.get(header);
                    checkString(value);
                    values.put(header, value.isEmpty() ? null : value);
                    empty &= value.isBlank();
                }
                if (!empty) addRow(file, null, Math.toIntExact(record.getRecordNumber() + 1),
                        json.valueToTree(values), type, rows, errors);
            }
            return 1;
        }
    }

    private int parseXlsx(InputStream input, String file, ImportType type, List<ParsedRow> rows,
            List<ImportError> errors) throws Exception {
        ZipSecureFile.setMinInflateRatio(1.0 / MAX_RATIO);
        ZipSecureFile.setMaxEntrySize(MAX_ENTRY_BYTES);
        ZipSecureFile.setMaxTextSize(MAX_TOTAL_BYTES);
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() > MAX_SHEETS) throw invalid("XLSX exceeds sheet limit");
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            int totalRows = 0;
            for (Sheet sheet : workbook) {
                Row headerRow = sheet.getRow(sheet.getFirstRowNum());
                if (headerRow == null) continue;
                int columns = headerRow.getLastCellNum();
                if (columns <= 0 || columns > MAX_COLUMNS) throw invalid("XLSX column count is invalid");
                List<String> headers = new ArrayList<>();
                for (int c = 0; c < columns; c++) headers.add(cellText(headerRow.getCell(c), formatter));
                validateHeaders(headers);
                for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    if (++totalRows > MAX_ROWS) throw invalid("XLSX exceeds row limit");
                    Map<String, String> values = new LinkedHashMap<>();
                    boolean empty = true;
                    for (int c = 0; c < columns; c++) {
                        String value = cellText(row.getCell(c), formatter);
                        values.put(headers.get(c), value.isEmpty() ? null : value);
                        empty &= value.isBlank();
                    }
                    if (!empty) addRow(file, sheet.getSheetName(), r + 1, json.valueToTree(values),
                            type, rows, errors);
                }
            }
            return 1;
        }
    }

    private int parseJson(InputStream input, String file, String sheet, ImportType type, List<ParsedRow> rows,
            List<ImportError> errors) throws IOException {
        JsonNode root = json.readTree(input);
        if (root == null || !root.isArray()) throw invalid("JSON root must be an array");
        if (root.size() > MAX_ROWS) throw invalid("JSON exceeds record limit");
        int index = 0;
        for (JsonNode node : root) {
            if (!node.isObject()) throw invalid("JSON records must be objects");
            addRow(file, sheet, ++index, node, type, rows, errors);
        }
        return 1;
    }

    private void addRow(String file, String sheet, int rowNumber, JsonNode node, ImportType type,
            List<ParsedRow> rows, List<ImportError> errors) {
        try {
            Object value = json.treeToValue(node, classFor(type));
            Set<? extends ConstraintViolation<?>> violations = validator.validate(value);
            if (!violations.isEmpty()) {
                ConstraintViolation<?> first = violations.iterator().next();
                errors.add(new ImportError(file, sheet, rowNumber, first.getPropertyPath().toString(),
                        "VALIDATION_ERROR", first.getMessage()));
            } else {
                rows.add(new ParsedRow(file, sheet, rows.size() + errors.size() + 1, node, value));
            }
        } catch (Exception ex) {
            errors.add(new ImportError(file, sheet, rowNumber, null, "INVALID_ROW", "row does not match schema"));
        }
    }

    private Class<?> classFor(ImportType type) {
        return switch (type) {
            case STUDENT -> StudentCreate.class;
            case KNOWLEDGE -> KnowledgeNodeCreateRequest.class;
            case LEARNING_RESOURCE -> ResourceCreate.class;
            case SCORE -> ExamCreate.class;
            case WRONG_QUESTION -> WrongCreate.class;
        };
    }

    private void validateHeaders(List<String> headers) {
        if (headers.isEmpty() || headers.size() > MAX_COLUMNS) throw invalid("header is required");
        Set<String> unique = new HashSet<>();
        for (String header : headers) {
            checkString(header);
            if (header.isBlank() || !unique.add(header)) throw invalid("headers must be non-empty and unique");
        }
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA) throw invalid("XLSX formulas are not supported");
        String value = formatter.formatCellValue(cell);
        checkString(value);
        return value;
    }

    private PushbackInputStream withoutUtf8Bom(InputStream input) throws IOException {
        PushbackInputStream pushback = new PushbackInputStream(input, 3);
        byte[] prefix = pushback.readNBytes(3);
        if (!(prefix.length == 3 && prefix[0] == (byte) 0xEF && prefix[1] == (byte) 0xBB
                && prefix[2] == (byte) 0xBF)) pushback.unread(prefix);
        return pushback;
    }

    private byte[] readBounded(InputStream input, long max) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        long size = 0;
        for (int read; (read = input.read(buffer)) >= 0;) {
            if (read == 0) continue;
            size += read;
            if (size > max) throw invalid("ZIP entry exceeds size limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void checkString(String value) {
        if (value != null && value.length() > MAX_STRING) throw invalid("cell text exceeds length limit");
    }

    private String suffix(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) throw invalid("file extension is required");
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private BusinessException invalid(String message) {
        return new BusinessException("IMPORT_FILE_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public record ParsedImport(List<ParsedRow> rows, List<ImportError> errors, int fileCount) { }
    public record ParsedRow(String file, String sheet, int rowNumber, JsonNode data, Object value) { }
    public record ImportError(String file, String sheet, int row, String field, String errorCode, String message) { }
}
