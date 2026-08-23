package com.stdntedu.backup.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.backup.entity.BackupRecordEntity;
import com.stdntedu.backup.entity.RestoreRecordEntity;
import com.stdntedu.backup.mapper.BackupRecordMapper;
import com.stdntedu.backup.mapper.RestoreRecordMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.RestoreTaskDto;
import com.stdntedu.generated.model.RestoreCancelRequest;
import com.stdntedu.generated.model.RestoreConflictStrategy;
import com.stdntedu.generated.model.RestoreCreate;
import com.stdntedu.generated.model.RestorePhase;
import com.stdntedu.generated.model.RestoreStatus;
import com.stdntedu.generated.model.RestoreTaskPageResponseAllOfData;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RestoreService {
    private final RestoreRecordMapper records;
    private final BackupRecordMapper backups;
    private final BackupRestoreDispatcher dispatcher;
    private final ObjectMapper json;
    private final IdConverter ids;
    private final SystemTimezoneProvider time;

    public RestoreService(RestoreRecordMapper records, BackupRecordMapper backups,
            BackupRestoreDispatcher dispatcher, ObjectMapper json, IdConverter ids, SystemTimezoneProvider time) {
        this.records = records;
        this.backups = backups;
        this.dispatcher = dispatcher;
        this.json = json;
        this.ids = ids;
        this.time = time;
    }

    @Transactional
    public RestoreTaskDto create(String backupId, RestoreCreate request) {
        if (request.getConfirmationText() == null) throw rule("confirmationText is invalid");
        if (Boolean.TRUE.equals(request.getCreatePreRestoreBackup())) {
            throw rule("createPreRestoreBackup is not supported in Local V1");
        }
        if (request.getConflictStrategy() != null && request.getConflictStrategy() != RestoreConflictStrategy.REPLACE) {
            throw rule("Local V1 supports REPLACE only");
        }
        Long sourceId = ids.toLong(backupId);
        BackupRecordEntity source = backups.selectById(sourceId);
        if (source == null) throw new ResourceNotFoundException("backup not found");
        if (!"SUCCESS".equals(source.getStatus()) || source.getVerifiedTime() == null) {
            throw conflict("backup must be successful and verified before restore");
        }
        boolean restoreSecrets = Boolean.TRUE.equals(request.getRestoreAiSecrets());
        if (restoreSecrets && !"INCLUDE_ENCRYPTED".equals(source.getSecretMode())) {
            throw rule("backup does not contain encrypted AI secrets");
        }
        if (activeTasks() > 0) throw conflict("another backup or restore task is active");
        RestoreOptions options = new RestoreOptions(!Boolean.FALSE.equals(request.getRestoreAttachments()),
                restoreSecrets, "REPLACE");
        RestoreRecordEntity record = new RestoreRecordEntity();
        record.setRestoreCode("RST-" + UUID.randomUUID().toString().replace("-", ""));
        record.setBackupId(sourceId); record.setStatus("PENDING"); record.setProgressStage("QUEUED");
        record.setProgressPercent(0); record.setOptionsJson(write(options)); record.setCancelRequested(false);
        record.setDatabaseApplied(false); record.setFilesFinalized(false); record.setRestoredTableCount(0);
        record.setRestoredAttachmentCount(0); record.setWarningCount(0);
        if (records.insert(record) != 1) throw new IllegalStateException("restore task was not created");
        afterCommit(record.getId());
        return get(record.getId().toString());
    }

    @Transactional(readOnly = true)
    public RestoreTaskPageResponseAllOfData list(String backupId, RestoreStatus status, OffsetDateTime start,
            OffsetDateTime end, Integer page, Integer pageSize) {
        if (start != null && end != null && !start.isBefore(end)) throw rule("startTime must be before endTime");
        int number = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        var query = Wrappers.<RestoreRecordEntity>lambdaQuery()
                .eq(backupId != null, RestoreRecordEntity::getBackupId, backupId == null ? null : ids.toLong(backupId))
                .eq(status != null, RestoreRecordEntity::getStatus, status == null ? null : status.getValue())
                .ge(start != null, RestoreRecordEntity::getCreateTime, start == null ? null : time.toLocalDateTime(start))
                .lt(end != null, RestoreRecordEntity::getCreateTime, end == null ? null : time.toLocalDateTime(end))
                .orderByDesc(RestoreRecordEntity::getCreateTime).orderByDesc(RestoreRecordEntity::getId);
        Page<RestoreRecordEntity> result = records.selectPage(new Page<>(number, size), query);
        return new RestoreTaskPageResponseAllOfData(number, size, result.getTotal(), Math.toIntExact(result.getPages()))
                .items(result.getRecords().stream().map(this::dto).toList());
    }

    @Transactional(readOnly = true)
    public RestoreTaskDto get(String id) { return dto(require(ids.toLong(id))); }

    @Transactional
    public RestoreTaskDto cancel(String id, RestoreCancelRequest request) {
        Long taskId = ids.toLong(id);
        RestoreRecordEntity current = require(taskId);
        if ("PENDING".equals(current.getStatus())) {
            int changed = records.update(null, Wrappers.<RestoreRecordEntity>lambdaUpdate()
                    .eq(RestoreRecordEntity::getId, taskId).eq(RestoreRecordEntity::getStatus, "PENDING")
                    .set(RestoreRecordEntity::getStatus, "CANCELLED")
                    .set(RestoreRecordEntity::getProgressStage, "CANCELLED")
                    .set(RestoreRecordEntity::getFinishTime, time.localDateTime()));
            if (changed != 1) throw conflict("restore task state changed");
        } else if ("RUNNING".equals(current.getStatus())
                && List.of("VERIFYING", "STAGING").contains(current.getProgressStage())) {
            int changed = records.update(null, Wrappers.<RestoreRecordEntity>lambdaUpdate()
                    .eq(RestoreRecordEntity::getId, taskId).eq(RestoreRecordEntity::getStatus, "RUNNING")
                    .in(RestoreRecordEntity::getProgressStage, List.of("VERIFYING", "STAGING"))
                    .set(RestoreRecordEntity::getCancelRequested, true));
            if (changed != 1) throw conflict("restore task passed the cancellable checkpoint");
        } else throw conflict("restore task cannot be cancelled in its current phase");
        return get(id);
    }

    RestoreRecordEntity require(Long id) {
        RestoreRecordEntity record = records.selectById(id);
        if (record == null) throw new ResourceNotFoundException("restore task not found");
        return record;
    }

    RestoreOptions options(RestoreRecordEntity record) {
        try { return json.readValue(record.getOptionsJson(), RestoreOptions.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("stored restore options are invalid", ex); }
    }

    private long activeTasks() {
        Long restoreCount = records.selectCount(Wrappers.<RestoreRecordEntity>lambdaQuery()
                .in(RestoreRecordEntity::getStatus, List.of("PENDING", "RUNNING")));
        Long backupCount = backups.selectCount(Wrappers.<BackupRecordEntity>lambdaQuery()
                .in(BackupRecordEntity::getStatus, List.of("PENDING", "RUNNING")));
        return restoreCount + backupCount;
    }

    private RestoreTaskDto dto(RestoreRecordEntity record) {
        RestoreOptions options = options(record);
        RestoreTaskDto dto = new RestoreTaskDto(record.getId().toString(), record.getBackupId().toString(),
                RestoreStatus.fromValue(record.getStatus()), record.getProgressPercent(),
                options.restoreAttachments(), options.restoreAiSecrets(), RestoreConflictStrategy.REPLACE,
                record.getCancelRequested(), record.getRestoredTableCount(), record.getRestoredAttachmentCount(),
                record.getWarningCount(), time.toOffsetDateTime(record.getCreateTime()));
        dto.setProgressStage(RestorePhase.fromValue(record.getProgressStage()));
        dto.setPreRestoreBackupId(record.getPreRestoreBackupId() == null ? null : record.getPreRestoreBackupId().toString());
        dto.setErrorCode(record.getErrorCode()); dto.setErrorMessage(record.getErrorMessage());
        dto.setStartedAt(time.toOffsetDateTime(record.getStartTime())); dto.setUpdatedAt(time.toOffsetDateTime(record.getUpdateTime()));
        dto.setFinishedAt(time.toOffsetDateTime(record.getFinishTime()));
        return dto;
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("restore options could not be stored", ex); }
    }

    private void afterCommit(Long id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { dispatcher.dispatchRestore(id); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { dispatcher.dispatchRestore(id); }
        });
    }

    private BusinessException rule(String message) { return new BusinessException("BUSINESS_RULE_VIOLATION", message,
            HttpStatus.UNPROCESSABLE_ENTITY); }
    private BusinessException conflict(String message) { return new BusinessException("TASK_STATE_CONFLICT", message,
            HttpStatus.CONFLICT); }
    public record RestoreOptions(boolean restoreAttachments, boolean restoreAiSecrets, String conflictStrategy) { }
}
