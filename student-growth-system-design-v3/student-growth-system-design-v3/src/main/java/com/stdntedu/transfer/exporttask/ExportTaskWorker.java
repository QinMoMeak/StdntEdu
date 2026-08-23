package com.stdntedu.transfer.exporttask;

import java.nio.file.Files;
import java.util.List;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.generated.model.ExportFormat;
import com.stdntedu.generated.model.ExportType;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import com.stdntedu.transfer.entity.ExportTaskEntity;
import com.stdntedu.transfer.exporttask.ExportArtifactWriter.Artifact;
import com.stdntedu.transfer.mapper.ExportTaskMapper;
import com.stdntedu.transfer.service.TransferFileService;
import com.stdntedu.transfer.service.TransferFileService.StoredAttachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExportTaskWorker {
    private static final Logger LOG = LoggerFactory.getLogger(ExportTaskWorker.class);
    private final ExportTaskMapper tasks;
    private final ExportDatasetCatalog catalog;
    private final ExportArtifactWriter writer;
    private final TransferFileService files;
    private final ObjectMapper json;
    private final SystemTimezoneProvider time;

    public ExportTaskWorker(ExportTaskMapper tasks, ExportDatasetCatalog catalog, ExportArtifactWriter writer,
            TransferFileService files, ObjectMapper json, SystemTimezoneProvider time) {
        this.tasks = tasks;
        this.catalog = catalog;
        this.writer = writer;
        this.files = files;
        this.json = json;
        this.time = time;
    }

    public void run(Long id) {
        if (tasks.update(null, Wrappers.<ExportTaskEntity>lambdaUpdate()
                .eq(ExportTaskEntity::getId, id).eq(ExportTaskEntity::getStatus, "PENDING")
                .set(ExportTaskEntity::getStatus, "RUNNING")
                .set(ExportTaskEntity::getStartedTime, time.localDateTime())
                .set(ExportTaskEntity::getProgressPercent, 1)) != 1) return;
        Artifact artifact = null;
        StoredAttachment stored = null;
        try {
            ExportTaskEntity task = tasks.selectById(id);
            List<String> rawTypes = json.readValue(task.getExportTypesJson(), new TypeReference<>() { });
            List<ExportType> types = rawTypes.stream().map(ExportType::fromValue).toList();
            ExportTaskService.ExportFilter filter = json.readValue(task.getFilterJson(),
                    ExportTaskService.ExportFilter.class);
            var specs = catalog.specs(types, task.getStudentId(), filter.startDate(), filter.endDate(),
                    Boolean.TRUE.equals(task.getIncludeDeleted()), filter.includeAiAnalysis());
            artifact = writer.write(id, ExportFormat.fromValue(task.getExportFormat()), specs);
            stored = files.storeGenerated(artifact.path(), artifact.fileName(), artifact.mimeType());
            int changed = tasks.update(null, Wrappers.<ExportTaskEntity>lambdaUpdate()
                    .eq(ExportTaskEntity::getId, id).eq(ExportTaskEntity::getStatus, "RUNNING")
                    .set(ExportTaskEntity::getStatus, "SUCCESS")
                    .set(ExportTaskEntity::getOutputAttachmentId, stored.entity().getId())
                    .set(ExportTaskEntity::getProgressPercent, 100)
                    .set(ExportTaskEntity::getFinishedTime, time.localDateTime()));
            if (changed != 1) files.cleanup(stored);
        } catch (Exception ex) {
            tasks.update(null, Wrappers.<ExportTaskEntity>lambdaUpdate()
                    .eq(ExportTaskEntity::getId, id).eq(ExportTaskEntity::getStatus, "RUNNING")
                    .set(ExportTaskEntity::getStatus, "FAILED")
                    .set(ExportTaskEntity::getErrorCode, "EXPORT_GENERATION_FAILED")
                    .set(ExportTaskEntity::getErrorMessage, "export file generation failed")
                    .set(ExportTaskEntity::getFinishedTime, time.localDateTime()));
            if (stored != null) files.cleanup(stored);
            LOG.warn("Export generation failed for task {}", id);
        } finally {
            if (artifact != null) try { Files.deleteIfExists(artifact.path()); } catch (Exception ignored) { }
        }
    }
}
