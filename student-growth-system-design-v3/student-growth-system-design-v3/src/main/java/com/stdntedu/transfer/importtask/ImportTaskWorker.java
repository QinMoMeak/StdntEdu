package com.stdntedu.transfer.importtask;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.extraction.entity.AttachmentEntity;
import com.stdntedu.generated.model.ImportType;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import com.stdntedu.transfer.entity.ImportTaskEntity;
import com.stdntedu.transfer.importtask.ImportFileParser.ParsedImport;
import com.stdntedu.transfer.mapper.ImportTaskMapper;
import com.stdntedu.transfer.service.TransferFileService;
import com.stdntedu.transfer.service.TransferFileService.StoredAttachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ImportTaskWorker {
    private static final Logger LOG = LoggerFactory.getLogger(ImportTaskWorker.class);
    private final ImportTaskMapper tasks;
    private final TransferFileService files;
    private final ImportFileParser parser;
    private final ImportErrorReportWriter reports;
    private final ImportDomainWriter writer;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final SystemTimezoneProvider time;

    public ImportTaskWorker(ImportTaskMapper tasks, TransferFileService files, ImportFileParser parser,
            ImportErrorReportWriter reports, ImportDomainWriter writer, ObjectMapper json,
            TransactionTemplate transactions, SystemTimezoneProvider time) {
        this.tasks = tasks;
        this.files = files;
        this.parser = parser;
        this.reports = reports;
        this.writer = writer;
        this.json = json;
        this.transactions = transactions;
        this.time = time;
    }

    public void run(Long taskId) {
        if (claim(taskId, "UPLOADED", "VALIDATING")) validate(taskId);
        else if (claim(taskId, "CONFIRM_PENDING", "IMPORTING")) importRows(taskId);
    }

    private boolean claim(Long id, String from, String to) {
        return tasks.update(null, Wrappers.<ImportTaskEntity>lambdaUpdate()
                .eq(ImportTaskEntity::getId, id).eq(ImportTaskEntity::getStatus, from)
                .set(ImportTaskEntity::getStatus, to).set(ImportTaskEntity::getStartedTime, time.localDateTime())
                .set(ImportTaskEntity::getProgressPercent, 1)) == 1;
    }

    private void validate(Long id) {
        StoredAttachment errorReport = null;
        Path temporary = null;
        try {
            ImportTaskEntity task = tasks.selectById(id);
            AttachmentEntity attachment = files.require(task.getAttachmentId());
            ParsedImport parsed = parser.parse(files.path(task.getAttachmentId()), attachment.getFileName(),
                    ImportType.fromValue(task.getImportType()));
            if (!parsed.errors().isEmpty()) {
                temporary = reports.write(parsed.errors());
                errorReport = files.storeGenerated(temporary, "import-" + id + "-errors.csv", "text/csv");
            }
            String preview = json.writeValueAsString(Map.of("rows", parsed.rows().stream().limit(100)
                    .map(row -> {
                        Map<String, Object> value = new LinkedHashMap<>();
                        value.put("file", row.file());
                        value.put("sheet", row.sheet());
                        value.put("rowNumber", row.rowNumber());
                        value.put("data", row.data());
                        return value;
                    }).toList()));
            int changed = tasks.update(null, Wrappers.<ImportTaskEntity>lambdaUpdate()
                    .eq(ImportTaskEntity::getId, id).eq(ImportTaskEntity::getStatus, "VALIDATING")
                    .set(ImportTaskEntity::getStatus, "PREVIEW_READY")
                    .set(ImportTaskEntity::getTotalRows, parsed.rows().size() + parsed.errors().size())
                    .set(ImportTaskEntity::getValidRows, parsed.rows().size())
                    .set(ImportTaskEntity::getInvalidRows, parsed.errors().size())
                    .set(ImportTaskEntity::getInputFileCount, parsed.fileCount())
                    .set(ImportTaskEntity::getPreviewJson, preview)
                    .set(ImportTaskEntity::getErrorReportAttachmentId,
                            errorReport == null ? null : errorReport.entity().getId())
                    .set(ImportTaskEntity::getProgressPercent, 100));
            if (changed != 1 && errorReport != null) files.cleanup(errorReport);
            else if (changed == 1 && task.getErrorReportAttachmentId() != null
                    && (errorReport == null || !task.getErrorReportAttachmentId().equals(errorReport.entity().getId()))) {
                files.cleanup(task.getErrorReportAttachmentId());
            }
        } catch (Exception ex) {
            if (errorReport != null) files.cleanup(errorReport);
            fail(id, "IMPORT_VALIDATION_FAILED", "import file validation failed", "VALIDATING");
            LOG.warn("Import validation failed for task {}", id);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
        }
    }

    private void importRows(Long id) {
        try {
            ImportTaskEntity task = tasks.selectById(id);
            AttachmentEntity attachment = files.require(task.getAttachmentId());
            ParsedImport parsed = parser.parse(files.path(task.getAttachmentId()), attachment.getFileName(),
                    ImportType.fromValue(task.getImportType()));
            ImportTaskService.NormalizedConfirm confirm = json.readValue(task.getConfirmRequestJson(),
                    ImportTaskService.NormalizedConfirm.class);
            Set<Integer> selected = Set.copyOf(confirm.selectedRows());
            Set<Integer> validIndexes = parsed.rows().stream().map(ImportFileParser.ParsedRow::rowNumber)
                    .collect(Collectors.toSet());
            if (!validIndexes.containsAll(selected)) throw new IllegalArgumentException("selectedRows contains invalid rows");
            if (!confirm.skipInvalidRows() && !parsed.errors().isEmpty()) {
                throw new IllegalArgumentException("invalid rows are not allowed");
            }
            transactions.executeWithoutResult(ignored -> complete(id, task, parsed, selected, confirm));
        } catch (Exception ex) {
            fail(id, "IMPORT_WRITE_FAILED", "import transaction failed", "IMPORTING");
            LOG.warn("Import execution failed for task {}", id);
        }
    }

    private void complete(Long id, ImportTaskEntity snapshot, ParsedImport parsed, Set<Integer> selected,
            ImportTaskService.NormalizedConfirm confirm) {
        ImportTaskEntity locked = tasks.selectForUpdate(id);
        if (locked == null || !"IMPORTING".equals(locked.getStatus())) return;
        int imported = writer.write(ImportType.fromValue(snapshot.getImportType()), snapshot.getStudentId(),
                parsed.rows(), selected);
        int selectedValid = selected.isEmpty() ? parsed.rows().size() : selected.size();
        int skipped = parsed.errors().size() + Math.max(0, parsed.rows().size() - selectedValid);
        String status = skipped > 0 ? "PARTIAL_SUCCESS" : "SUCCESS";
        int changed = tasks.update(null, Wrappers.<ImportTaskEntity>lambdaUpdate()
                .eq(ImportTaskEntity::getId, id).eq(ImportTaskEntity::getStatus, "IMPORTING")
                .set(ImportTaskEntity::getStatus, status)
                .set(ImportTaskEntity::getImportedRows, imported)
                .set(ImportTaskEntity::getSkippedRows, skipped)
                .set(ImportTaskEntity::getFailedRows, 0)
                .set(ImportTaskEntity::getProgressPercent, 100)
                .set(ImportTaskEntity::getFinishedTime, time.localDateTime()));
        if (changed != 1) throw new IllegalStateException("import task state changed");
    }

    private void fail(Long id, String code, String message, String expectedStatus) {
        tasks.update(null, Wrappers.<ImportTaskEntity>lambdaUpdate()
                .eq(ImportTaskEntity::getId, id).eq(ImportTaskEntity::getStatus, expectedStatus)
                .set(ImportTaskEntity::getStatus, "FAILED")
                .set(ImportTaskEntity::getErrorCode, code).set(ImportTaskEntity::getErrorMessage, message)
                .set(ImportTaskEntity::getFinishedTime, time.localDateTime()));
    }
}
