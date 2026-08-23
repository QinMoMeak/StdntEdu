package com.stdntedu.transfer.importtask;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

@Component
public class ImportErrorReportWriter {
    public Path write(List<ImportFileParser.ImportError> errors) throws IOException {
        Path file = Files.createTempFile("import-errors-", ".csv");
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
                CSVPrinter csv = new CSVPrinter(writer, CSVFormat.RFC4180.builder()
                        .setHeader("file", "sheet", "row", "field", "errorCode", "message").get())) {
            for (ImportFileParser.ImportError error : errors) {
                csv.printRecord(error.file(), error.sheet(), error.row(), error.field(),
                        error.errorCode(), error.message());
            }
        }
        return file;
    }
}
