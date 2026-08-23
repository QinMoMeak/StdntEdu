package com.stdntedu.backup.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stdntedu.ai.extraction.resource.OriginalFileStorage;
import com.stdntedu.ai.model.security.AiSecretCryptoService;
import com.stdntedu.backup.entity.BackupRecordEntity;
import com.stdntedu.backup.mapper.BackupRecordMapper;
import com.stdntedu.backup.mapper.RestoreRecordMapper;
import com.stdntedu.backup.entity.RestoreRecordEntity;
import com.stdntedu.backup.packageformat.BackupArchiveService;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.Backup;
import com.stdntedu.generated.model.BackupCreate;
import com.stdntedu.generated.model.BackupPageResponseAllOfData;
import com.stdntedu.generated.model.BackupSecretMode;
import com.stdntedu.generated.model.BackupStatus;
import com.stdntedu.generated.model.BackupType;
import com.stdntedu.generated.model.BackupVerifyDto;
import com.stdntedu.generated.model.CompressionType;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class BackupService {
    private final BackupRecordMapper records;
    private final RestoreRecordMapper restores;
    private final BackupArchiveService archives;
    private final OriginalFileStorage storage;
    private final AiSecretCryptoService secrets;
    private final BackupRestoreDispatcher dispatcher;
    private final IdConverter ids;
    private final SystemTimezoneProvider time;
    private final String applicationVersion;

    public BackupService(BackupRecordMapper records, RestoreRecordMapper restores,
            BackupArchiveService archives, OriginalFileStorage storage,
            AiSecretCryptoService secrets, BackupRestoreDispatcher dispatcher, IdConverter ids,
            SystemTimezoneProvider time, @Value("${app.version:unknown}") String applicationVersion) {
        this.records = records;
        this.restores = restores;
        this.archives = archives;
        this.storage = storage;
        this.secrets = secrets;
        this.dispatcher = dispatcher;
        this.ids = ids;
        this.time = time;
        this.applicationVersion = applicationVersion;
    }

    @Transactional
    public Backup create(BackupCreate request) {
        if (request.getBackupType() != BackupType.FULL) throw rule("Local V1 supports FULL backup only");
        BackupSecretMode secretMode = request.getSecretMode() == null ? BackupSecretMode.EXCLUDE : request.getSecretMode();
        if (secretMode == BackupSecretMode.INCLUDE_ENCRYPTED) secrets.masterKeyFingerprint();
        if (activeTasks() > 0) throw conflict("another backup or restore task is active");
        BackupRecordEntity record = new BackupRecordEntity();
        record.setBackupCode("BKP-" + UUID.randomUUID().toString().replace("-", ""));
        record.setBackupType("FULL");
        record.setFormat(BackupArchiveService.FORMAT);
        record.setManifestSchemaVersion(BackupArchiveService.SCHEMA_VERSION);
        record.setCompression(BackupArchiveService.COMPRESSION);
        record.setSecretMode(secretMode.getValue());
        record.setIncludeAttachments(!Boolean.FALSE.equals(request.getIncludeAttachments()));
        record.setStatus("PENDING");
        record.setSystemVersion(applicationVersion);
        record.setRemark(request.getRemark());
        record.setDeleted(false);
        if (records.insert(record) != 1) throw new IllegalStateException("backup task was not created");
        afterCommit(record.getId());
        return get(record.getId().toString());
    }

    @Transactional(readOnly = true)
    public BackupPageResponseAllOfData list(BackupType type, BackupStatus status, OffsetDateTime start,
            OffsetDateTime end, Integer page, Integer pageSize) {
        if (start != null && end != null && !start.isBefore(end)) throw rule("startTime must be before endTime");
        int number = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        var query = Wrappers.<BackupRecordEntity>lambdaQuery()
                .eq(type != null, BackupRecordEntity::getBackupType, type == null ? null : type.getValue())
                .eq(status != null, BackupRecordEntity::getStatus, status == null ? null : status.getValue())
                .ge(start != null, BackupRecordEntity::getCreateTime, start == null ? null : time.toLocalDateTime(start))
                .lt(end != null, BackupRecordEntity::getCreateTime, end == null ? null : time.toLocalDateTime(end))
                .orderByDesc(BackupRecordEntity::getCreateTime).orderByDesc(BackupRecordEntity::getId);
        Page<BackupRecordEntity> result = records.selectPage(new Page<>(number, size), query);
        return new BackupPageResponseAllOfData(number, size, result.getTotal(), Math.toIntExact(result.getPages()))
                .items(result.getRecords().stream().map(this::dto).toList());
    }

    @Transactional(readOnly = true)
    public Backup get(String id) { return dto(require(ids.toLong(id))); }

    @Transactional
    public BackupVerifyDto verify(String id) {
        BackupRecordEntity record = require(ids.toLong(id));
        var result = archives.verify(record);
        record.setVerifiedTime(time.localDateTime());
        records.updateById(record);
        return new BackupVerifyDto(true, true, true, true, true, true, true, true,
                result.warnings(), time.offsetDateTime())
                .datasetCount(result.manifest().datasetCount()).recordCount(result.manifest().recordCount())
                .attachmentCount(result.manifest().attachmentCount());
    }

    @Transactional(readOnly = true)
    public Download download(String id) {
        BackupRecordEntity record = require(ids.toLong(id));
        if (!"SUCCESS".equals(record.getStatus()) || record.getStoragePath() == null) {
            throw conflict("backup artifact is not ready");
        }
        Path path = storage.requireStoredFile(Path.of(record.getStoragePath()));
        try { return new Download(new InputStreamResource(Files.newInputStream(path)), record.getFileName(),
                record.getFileSize()); }
        catch (Exception ex) { throw new BusinessException("STORAGE_FILE_MISSING", "backup artifact is unavailable",
                HttpStatus.INTERNAL_SERVER_ERROR); }
    }

    @Transactional
    public void delete(String id) {
        BackupRecordEntity record = require(ids.toLong(id));
        if (!List.of("SUCCESS", "FAILED").contains(record.getStatus())) {
            throw conflict("backup cannot be deleted in its current state");
        }
        if (restores.selectCount(Wrappers.<RestoreRecordEntity>lambdaQuery()
                .eq(RestoreRecordEntity::getBackupId, record.getId())
                .in(RestoreRecordEntity::getStatus, List.of("PENDING", "RUNNING"))) > 0) {
            throw conflict("backup is used by an active restore task");
        }
        if (record.getStoragePath() != null) archives.deleteArtifact(record);
        if (records.deleteById(record.getId()) != 1) throw new IllegalStateException("backup record was not deleted");
    }

    BackupRecordEntity require(Long id) {
        BackupRecordEntity record = records.selectById(id);
        if (record == null) throw new ResourceNotFoundException("backup not found");
        return record;
    }

    private long activeTasks() {
        Long backups = records.selectCount(Wrappers.<BackupRecordEntity>lambdaQuery()
                .in(BackupRecordEntity::getStatus, List.of("PENDING", "RUNNING")));
        Long restoreCount = restores.selectCount(Wrappers.<RestoreRecordEntity>lambdaQuery()
                .in(RestoreRecordEntity::getStatus, List.of("PENDING", "RUNNING")));
        return backups + restoreCount;
    }

    private Backup dto(BackupRecordEntity record) {
        Backup dto = new Backup();
        dto.setId(record.getId().toString()); dto.setBackupCode(record.getBackupCode());
        dto.setBackupType(BackupType.FULL); dto.setStatus(BackupStatus.fromValue(record.getStatus()));
        dto.setFormat(Backup.FormatEnum.STDNTEDU_BACKUP_V1);
        dto.setManifestSchemaVersion(Backup.ManifestSchemaVersionEnum.NUMBER_1);
        dto.setCompression(CompressionType.ZIP_DEFLATE); dto.setEncrypted(false);
        dto.setSecretMode(BackupSecretMode.fromValue(record.getSecretMode()));
        dto.setIncludeAttachments(record.getIncludeAttachments()); dto.setCreatedAt(time.toOffsetDateTime(record.getCreateTime()));
        dto.setFileName(record.getFileName()); dto.setFileSize(record.getFileSize()); dto.setChecksum(record.getChecksum());
        dto.setSystemVersion(record.getSystemVersion()); dto.setDatabaseVersion(record.getDatabaseVersion());
        dto.setDatasetCount(record.getDatasetCount()); dto.setRecordCount(record.getRecordCount());
        dto.setAttachmentCount(record.getAttachmentCount()); dto.setVerifiedAt(time.toOffsetDateTime(record.getVerifiedTime()));
        dto.setRemark(record.getRemark()); dto.setErrorCode(record.getErrorCode()); dto.setErrorMessage(record.getErrorMessage());
        dto.setStartedAt(time.toOffsetDateTime(record.getStartTime())); dto.setUpdatedAt(time.toOffsetDateTime(record.getUpdateTime()));
        dto.setFinishedAt(time.toOffsetDateTime(record.getFinishTime()));
        return dto;
    }

    private void afterCommit(Long id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { dispatcher.dispatchBackup(id); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { dispatcher.dispatchBackup(id); }
        });
    }

    private BusinessException rule(String message) { return new BusinessException("BUSINESS_RULE_VIOLATION", message,
            HttpStatus.UNPROCESSABLE_ENTITY); }
    private BusinessException conflict(String message) { return new BusinessException("TASK_STATE_CONFLICT", message,
            HttpStatus.CONFLICT); }
    public record Download(org.springframework.core.io.Resource content, String fileName, long size) { }
}
