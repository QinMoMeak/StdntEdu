package com.stdntedu.backup.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.ai.extraction.resource.OriginalFileStorage;
import com.stdntedu.ai.model.security.AiSecretCryptoService;
import com.stdntedu.backup.entity.BackupRecordEntity;
import com.stdntedu.backup.entity.RestoreRecordEntity;
import com.stdntedu.backup.mapper.BackupRecordMapper;
import com.stdntedu.backup.mapper.RestoreRecordMapper;
import com.stdntedu.backup.packageformat.BackupArchiveService;
import com.stdntedu.backup.packageformat.BackupManifest;
import com.stdntedu.backup.packageformat.LogicalBackupDataService;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RestoreTaskWorker {
    private static final Logger LOG = LoggerFactory.getLogger(RestoreTaskWorker.class);
    private static final long MAX_ATTACHMENT_BYTES = 500L * 1024 * 1024;
    private final RestoreRecordMapper records;
    private final BackupRecordMapper backups;
    private final BackupArchiveService archives;
    private final LogicalBackupDataService data;
    private final OriginalFileStorage storage;
    private final AiSecretCryptoService secrets;
    private final BackupRestoreLock lock;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SystemTimezoneProvider time;

    public RestoreTaskWorker(RestoreRecordMapper records, BackupRecordMapper backups,
            BackupArchiveService archives, LogicalBackupDataService data, OriginalFileStorage storage,
            AiSecretCryptoService secrets, BackupRestoreLock lock,
            JdbcTemplate jdbc, ObjectMapper json, SystemTimezoneProvider time) {
        this.records = records; this.backups = backups; this.archives = archives; this.data = data;
        this.storage = storage; this.secrets = secrets; this.lock = lock;
        this.jdbc = jdbc; this.json = json; this.time = time;
    }

    public void run(Long id) { lock.run(() -> runLocked(id)); }

    private void runLocked(Long id) {
        RestoreRecordEntity existing = records.selectById(id);
        if (existing == null) return;
        if ("RUNNING".equals(existing.getStatus()) && Boolean.TRUE.equals(existing.getDatabaseApplied())
                && "FINALIZING".equals(existing.getProgressStage())) {
            resumeFinalization(existing);
            return;
        }
        if (records.update(null, Wrappers.<RestoreRecordEntity>lambdaUpdate()
                .eq(RestoreRecordEntity::getId, id).eq(RestoreRecordEntity::getStatus, "PENDING")
                .set(RestoreRecordEntity::getStatus, "RUNNING")
                .set(RestoreRecordEntity::getProgressStage, "VERIFYING")
                .set(RestoreRecordEntity::getProgressPercent, 5)
                .set(RestoreRecordEntity::getStartTime, time.localDateTime())) != 1) return;
        List<Path> newFiles = new ArrayList<>();
        try {
            RestoreRecordEntity task = records.selectById(id);
            RestoreService.RestoreOptions options = json.readValue(task.getOptionsJson(),
                    RestoreService.RestoreOptions.class);
            BackupRecordEntity backup = backups.selectById(task.getBackupId());
            if (backup == null) throw new IllegalStateException("source backup is unavailable");
            var verified = archives.verify(backup);
            preflight(verified.manifest(), options);
            records.update(null, Wrappers.<RestoreRecordEntity>lambdaUpdate().eq(RestoreRecordEntity::getId, id)
                    .eq(RestoreRecordEntity::getStatus, "RUNNING")
                    .set(RestoreRecordEntity::getInputManifestJson, write(verified.manifest()))
                    .set(RestoreRecordEntity::getProgressPercent, 15));
            if (cancelIfRequested(id, newFiles)) return;

            try (var staged = archives.stage(backup)) {
                setPhase(id, "STAGING", 25);
                Map<Long, String> newPaths = new LinkedHashMap<>();
                if (options.restoreAttachments()) {
                    for (BackupManifest.Entry entry : staged.manifest().attachments()) {
                        Path source = staged.root().resolve(entry.path()).normalize();
                        try (InputStream input = Files.newInputStream(source)) {
                            var stored = storage.persist(input, ".bin", MAX_ATTACHMENT_BYTES);
                            if (stored.size() != entry.size() || !stored.sha256().equals(entry.sha256())) {
                                storage.cleanup(stored.path());
                                throw new IllegalStateException("restored attachment checksum does not match");
                            }
                            newFiles.add(stored.path());
                            newPaths.put(entry.attachmentId(), stored.path().toString());
                        }
                    }
                }
                List<String> oldPaths = jdbc.queryForList("SELECT storage_path FROM attachment", String.class);
                RestoreCheckpoint checkpoint = new RestoreCheckpoint(newPaths, oldPaths);
                records.update(null, Wrappers.<RestoreRecordEntity>lambdaUpdate()
                        .eq(RestoreRecordEntity::getId, id).eq(RestoreRecordEntity::getStatus, "RUNNING")
                        .set(RestoreRecordEntity::getCheckpointJson, write(checkpoint))
                        .set(RestoreRecordEntity::getProgressPercent, 40));
                if (cancelIfRequested(id, newFiles)) return;

                setPhase(id, "APPLYING", 50);
                var result = data.restore(staged.root(), staged.manifest(), newPaths,
                        options.restoreAttachments(), options.restoreAiSecrets(), () -> records.update(null,
                                Wrappers.<RestoreRecordEntity>lambdaUpdate()
                                        .eq(RestoreRecordEntity::getId, id).eq(RestoreRecordEntity::getStatus, "RUNNING")
                                        .set(RestoreRecordEntity::getDatabaseApplied, true)
                                        .set(RestoreRecordEntity::getProgressStage, "FINALIZING")
                                        .set(RestoreRecordEntity::getProgressPercent, 90)
                                        .set(RestoreRecordEntity::getRestoredTableCount, staged.manifest().datasetCount())
                                        .set(RestoreRecordEntity::getRestoredAttachmentCount,
                                                options.restoreAttachments() ? staged.manifest().attachmentCount() : 0)));
                finalizeFiles(oldPaths);
                records.update(null, Wrappers.<RestoreRecordEntity>lambdaUpdate()
                        .eq(RestoreRecordEntity::getId, id).eq(RestoreRecordEntity::getStatus, "RUNNING")
                        .set(RestoreRecordEntity::getStatus, "SUCCESS")
                        .set(RestoreRecordEntity::getProgressStage, "COMPLETED")
                        .set(RestoreRecordEntity::getProgressPercent, 100)
                        .set(RestoreRecordEntity::getFilesFinalized, true)
                        .set(RestoreRecordEntity::getRestoredTableCount, result.tableCount())
                        .set(RestoreRecordEntity::getFinishTime, time.localDateTime()));
            }
        } catch (Exception ex) {
            RestoreRecordEntity current = records.selectById(id);
            if (current != null && Boolean.TRUE.equals(current.getDatabaseApplied())) {
                LOG.warn("Restore finalization failed for task {}; periodic recovery will retry", id);
                return;
            }
            newFiles.forEach(storage::cleanup);
            records.update(null, Wrappers.<RestoreRecordEntity>lambdaUpdate()
                    .eq(RestoreRecordEntity::getId, id).eq(RestoreRecordEntity::getStatus, "RUNNING")
                    .set(RestoreRecordEntity::getStatus, "FAILED")
                    .set(RestoreRecordEntity::getProgressStage, "FAILED")
                    .set(RestoreRecordEntity::getErrorCode, "RESTORE_FAILED")
                    .set(RestoreRecordEntity::getErrorMessage, "backup restore failed")
                    .set(RestoreRecordEntity::getFinishTime, time.localDateTime()));
            LOG.warn("Restore failed for task {}", id);
        }
    }

    private void preflight(BackupManifest manifest, RestoreService.RestoreOptions options) {
        if (!time.get().getId().equals(manifest.timezone())) {
            throw new IllegalStateException("backup timezone is not compatible");
        }
        if (options.restoreAiSecrets()) {
            if (!"INCLUDE_ENCRYPTED".equals(manifest.secretMode())
                    || !secrets.masterKeyFingerprint().equals(manifest.masterKeyFingerprint())) {
                throw new IllegalStateException("AI secret master key fingerprint does not match");
            }
        }
    }

    private boolean cancelIfRequested(Long id, List<Path> newFiles) {
        RestoreRecordEntity task = records.selectById(id);
        if (!Boolean.TRUE.equals(task.getCancelRequested())) return false;
        newFiles.forEach(storage::cleanup);
        records.update(null, Wrappers.<RestoreRecordEntity>lambdaUpdate()
                .eq(RestoreRecordEntity::getId, id).eq(RestoreRecordEntity::getStatus, "RUNNING")
                .in(RestoreRecordEntity::getProgressStage, List.of("VERIFYING", "STAGING"))
                .set(RestoreRecordEntity::getStatus, "CANCELLED")
                .set(RestoreRecordEntity::getProgressStage, "CANCELLED")
                .set(RestoreRecordEntity::getFinishTime, time.localDateTime()));
        return true;
    }

    private void resumeFinalization(RestoreRecordEntity task) {
        try {
            RestoreCheckpoint checkpoint = json.readValue(task.getCheckpointJson(), RestoreCheckpoint.class);
            finalizeFiles(checkpoint.oldPaths());
            records.update(null, Wrappers.<RestoreRecordEntity>lambdaUpdate()
                    .eq(RestoreRecordEntity::getId, task.getId()).eq(RestoreRecordEntity::getStatus, "RUNNING")
                    .eq(RestoreRecordEntity::getProgressStage, "FINALIZING")
                    .set(RestoreRecordEntity::getStatus, "SUCCESS")
                    .set(RestoreRecordEntity::getProgressStage, "COMPLETED")
                    .set(RestoreRecordEntity::getProgressPercent, 100)
                    .set(RestoreRecordEntity::getFilesFinalized, true)
                    .set(RestoreRecordEntity::getFinishTime, time.localDateTime()));
        } catch (Exception ex) { throw new IllegalStateException("restore finalization failed", ex); }
    }

    private void finalizeFiles(List<String> oldPaths) throws Exception {
        for (String value : oldPaths) {
            Path path = Path.of(value);
            if (storage.isManagedPath(path)) Files.deleteIfExists(path);
        }
    }

    private void setPhase(Long id, String phase, int progress) {
        if (records.update(null, Wrappers.<RestoreRecordEntity>lambdaUpdate()
                .eq(RestoreRecordEntity::getId, id).eq(RestoreRecordEntity::getStatus, "RUNNING")
                .set(RestoreRecordEntity::getProgressStage, phase)
                .set(RestoreRecordEntity::getProgressPercent, progress)) != 1) {
            throw new IllegalStateException("restore task state changed");
        }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("restore checkpoint failed", ex); }
    }

    public record RestoreCheckpoint(Map<Long, String> newPaths, List<String> oldPaths) { }
}
