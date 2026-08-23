package com.stdntedu.stage12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.generated.model.ImportType;
import com.stdntedu.transfer.importtask.ImportFileParser;
import jakarta.validation.Validation;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ImportFileParserSecurityTest {
    private final ImportFileParser parser = new ImportFileParser(
            new ObjectMapper().findAndRegisterModules(), Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void csvAcceptsUtf8BomAndRejectsDuplicateHeaders() throws Exception {
        Path valid = temp(".csv", concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                "name,currentStageId,currentGradeId\r\nAlice,1,1\r\n".getBytes(StandardCharsets.UTF_8)));
        assertThat(parser.parse(valid, "students.csv", ImportType.STUDENT).rows()).hasSize(1);

        Path duplicate = temp(".csv", "name,name,currentStageId,currentGradeId\r\nA,B,1,1\r\n"
                .getBytes(StandardCharsets.UTF_8));
        assertInvalid(duplicate, "students.csv");
    }

    @Test
    void jsonRejectsNonArrayExcessDepthAndUnknownFields() throws Exception {
        assertInvalid(temp(".json", "{}".getBytes(StandardCharsets.UTF_8)), "students.json");
        String deep = "[{\"name\":\"A\",\"currentStageId\":\"1\",\"currentGradeId\":\"1\",\"x\":"
                + "[".repeat(25) + "0" + "]".repeat(25) + "}]";
        assertInvalid(temp(".json", deep.getBytes(StandardCharsets.UTF_8)), "students.json");
        var parsed = parser.parse(temp(".json", "[{\"name\":\"A\",\"currentStageId\":\"1\",\"currentGradeId\":\"1\",\"unknown\":true}]"
                .getBytes(StandardCharsets.UTF_8)), "students.json", ImportType.STUDENT);
        assertThat(parsed.errors()).hasSize(1);
    }

    @Test
    void xlsxRejectsFormulaCells() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("students");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("name");
            header.createCell(1).setCellValue("currentStageId");
            header.createCell(2).setCellValue("currentGradeId");
            var row = sheet.createRow(1);
            row.createCell(0).setCellFormula("1+1");
            row.createCell(1).setCellValue("1");
            row.createCell(2).setCellValue("1");
            workbook.write(bytes);
        }
        assertInvalid(temp(".xlsx", bytes.toByteArray()), "students.xlsx");
    }

    @Test
    void xlsxAndZipParseValidStudentRows() throws Exception {
        ByteArrayOutputStream xlsx = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("students");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("name");
            header.createCell(1).setCellValue("currentStageId");
            header.createCell(2).setCellValue("currentGradeId");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Alice");
            row.createCell(1).setCellValue("1");
            row.createCell(2).setCellValue("1");
            workbook.write(xlsx);
        }
        assertThat(parser.parse(temp(".xlsx", xlsx.toByteArray()), "students.xlsx", ImportType.STUDENT).rows())
                .hasSize(1);
        byte[] json = "[{\"name\":\"Alice\",\"currentStageId\":\"1\",\"currentGradeId\":\"1\"}]"
                .getBytes(StandardCharsets.UTF_8);
        assertThat(parser.parse(temp(".zip", zip("students.json", json)), "students.zip", ImportType.STUDENT).rows())
                .hasSize(1);
    }

    @Test
    void zipRejectsTraversalNestedArchivesAndBombRatio() throws Exception {
        assertInvalid(temp(".zip", zip("../students.json", "[]".getBytes(StandardCharsets.UTF_8))), "data.zip");
        assertInvalid(temp(".zip", zip("nested.zip", zip("students.json", "[]".getBytes(StandardCharsets.UTF_8)))),
                "data.zip");
        assertInvalid(temp(".zip", zip("students.json", new byte[2 * 1024 * 1024])), "data.zip");
    }

    @Test
    void zipRejectsAbsoluteDriveUncSymlinkAndTooManyEntries() throws Exception {
        for (String name : new String[] {"/students.json", "C:/students.json", "\\\\server\\share\\students.json"}) {
            assertInvalid(temp(".zip", zip(name, "[]".getBytes(StandardCharsets.UTF_8))), "data.zip");
        }

        ByteArrayOutputStream symlink = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(symlink)) {
            ZipArchiveEntry entry = new ZipArchiveEntry("students.json");
            entry.setUnixMode(UnixStat.LINK_FLAG | 0777);
            output.putArchiveEntry(entry);
            output.write("target".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
        }
        assertInvalid(temp(".zip", symlink.toByteArray()), "data.zip");

        ByteArrayOutputStream many = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(many, StandardCharsets.UTF_8)) {
            for (int i = 0; i <= 20; i++) {
                output.putNextEntry(new ZipEntry("students-" + i + ".json"));
                output.write("[]".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        assertInvalid(temp(".zip", many.toByteArray()), "data.zip");
    }

    private void assertInvalid(Path path, String name) {
        assertThatThrownBy(() -> parser.parse(path, name, ImportType.STUDENT))
                .isInstanceOf(BusinessException.class);
    }

    private Path temp(String suffix, byte[] bytes) throws Exception {
        Path file = Files.createTempFile("stage12d-", suffix);
        Files.write(file, bytes);
        file.toFile().deleteOnExit();
        return file;
    }

    private byte[] zip(String name, byte[] content) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private byte[] concat(byte[] first, byte[] second) {
        byte[] result = java.util.Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
