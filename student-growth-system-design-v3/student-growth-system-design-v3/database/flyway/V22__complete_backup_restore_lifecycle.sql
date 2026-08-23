ALTER TABLE backup_record
    ADD COLUMN format VARCHAR(32) NOT NULL DEFAULT 'STDNTEDU_BACKUP_V1' AFTER backup_type,
    ADD COLUMN manifest_schema_version INT NOT NULL DEFAULT 1 AFTER format,
    ADD COLUMN compression VARCHAR(32) NOT NULL DEFAULT 'ZIP_DEFLATE' AFTER manifest_schema_version,
    ADD COLUMN secret_mode VARCHAR(32) NOT NULL DEFAULT 'EXCLUDE' AFTER compression,
    ADD COLUMN include_attachments TINYINT NOT NULL DEFAULT 1 AFTER secret_mode,
    ADD COLUMN database_version VARCHAR(32) NULL AFTER system_version,
    ADD COLUMN dataset_count INT NULL AFTER database_version,
    ADD COLUMN record_count BIGINT NULL AFTER dataset_count,
    ADD COLUMN attachment_count INT NULL AFTER record_count,
    ADD COLUMN manifest_json JSON NULL AFTER attachment_count,
    ADD COLUMN error_code VARCHAR(64) NULL AFTER remark,
    ADD COLUMN start_time DATETIME(3) NULL AFTER error_message,
    ADD COLUMN verified_time DATETIME(3) NULL AFTER start_time,
    ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER create_time,
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 AFTER finish_time;

UPDATE backup_record
SET deleted = 1,
    status = 'FAILED',
    error_code = COALESCE(error_code, 'LEGACY_BACKUP_UNAVAILABLE'),
    error_message = COALESCE(error_message, 'legacy deleted or expired backup is unavailable'),
    finish_time = COALESCE(finish_time, CURRENT_TIMESTAMP(3))
WHERE status IN ('DELETED', 'EXPIRED');

ALTER TABLE backup_record
    DROP CHECK chk_br_status_v3,
    ADD CONSTRAINT chk_br_status_v22 CHECK(status IN ('PENDING','RUNNING','SUCCESS','FAILED')),
    ADD CONSTRAINT chk_br_type_v22 CHECK(backup_type = 'FULL'),
    ADD CONSTRAINT chk_br_format_v22 CHECK(format = 'STDNTEDU_BACKUP_V1'),
    ADD CONSTRAINT chk_br_manifest_version_v22 CHECK(manifest_schema_version = 1),
    ADD CONSTRAINT chk_br_compression_v22 CHECK(compression = 'ZIP_DEFLATE'),
    ADD CONSTRAINT chk_br_secret_mode_v22 CHECK(secret_mode IN ('EXCLUDE','INCLUDE_ENCRYPTED')),
    ADD CONSTRAINT chk_br_counts_v22 CHECK(
        (dataset_count IS NULL OR dataset_count >= 0)
        AND (record_count IS NULL OR record_count >= 0)
        AND (attachment_count IS NULL OR attachment_count >= 0)
        AND (file_size IS NULL OR file_size >= 0)
    ),
    ADD CONSTRAINT chk_br_checksum_v22 CHECK(checksum IS NULL OR checksum REGEXP '^[0-9A-Fa-f]{64}$'),
    ADD INDEX idx_br_status_id(status,deleted,id),
    ADD INDEX idx_br_create_time(create_time,id);

ALTER TABLE restore_record
    ADD COLUMN options_json JSON NULL AFTER pre_restore_backup_id,
    ADD COLUMN input_manifest_json JSON NULL AFTER options_json,
    ADD COLUMN checkpoint_json JSON NULL AFTER input_manifest_json,
    ADD COLUMN cancel_requested TINYINT NOT NULL DEFAULT 0 AFTER checkpoint_json,
    ADD COLUMN database_applied TINYINT NOT NULL DEFAULT 0 AFTER cancel_requested,
    ADD COLUMN files_finalized TINYINT NOT NULL DEFAULT 0 AFTER database_applied,
    ADD COLUMN restored_table_count INT NOT NULL DEFAULT 0 AFTER files_finalized,
    ADD COLUMN restored_attachment_count INT NOT NULL DEFAULT 0 AFTER restored_table_count,
    ADD COLUMN warning_count INT NOT NULL DEFAULT 0 AFTER restored_attachment_count,
    ADD COLUMN error_code VARCHAR(64) NULL AFTER warning_count,
    ADD COLUMN start_time DATETIME(3) NULL AFTER error_message,
    ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER create_time;

UPDATE restore_record
SET status = 'FAILED',
    progress_stage = 'FAILED',
    error_code = COALESCE(error_code, 'LEGACY_RESTORE_INTERRUPTED'),
    error_message = COALESCE(error_message, 'legacy restore lifecycle cannot be resumed safely'),
    finish_time = COALESCE(finish_time, CURRENT_TIMESTAMP(3))
WHERE status NOT IN ('PENDING','SUCCESS','FAILED');

UPDATE restore_record
SET progress_stage = CASE status
        WHEN 'PENDING' THEN 'QUEUED'
        WHEN 'SUCCESS' THEN 'COMPLETED'
        ELSE 'FAILED'
    END,
    start_time = CASE WHEN status = 'PENDING' THEN NULL ELSE COALESCE(start_time, create_time) END,
    finish_time = CASE WHEN status = 'PENDING' THEN NULL ELSE COALESCE(finish_time, CURRENT_TIMESTAMP(3)) END;

ALTER TABLE restore_record
    DROP CHECK chk_rr_status_v3,
    MODIFY COLUMN progress_stage VARCHAR(64) NOT NULL DEFAULT 'QUEUED',
    ADD CONSTRAINT chk_rr_status_v22 CHECK(status IN ('PENDING','RUNNING','SUCCESS','FAILED','CANCELLED')),
    ADD CONSTRAINT chk_rr_phase_v22 CHECK(progress_stage IN ('QUEUED','VERIFYING','STAGING','APPLYING','FINALIZING','COMPLETED','FAILED','CANCELLED')),
    ADD CONSTRAINT chk_rr_counts_v22 CHECK(
        restored_table_count >= 0 AND restored_attachment_count >= 0 AND warning_count >= 0
    ),
    ADD CONSTRAINT chk_rr_lifecycle_v22 CHECK(
        (status = 'PENDING' AND progress_stage = 'QUEUED' AND start_time IS NULL AND finish_time IS NULL)
        OR (status = 'RUNNING' AND progress_stage IN ('VERIFYING','STAGING','APPLYING','FINALIZING')
            AND start_time IS NOT NULL AND finish_time IS NULL)
        OR (status = 'SUCCESS' AND progress_stage = 'COMPLETED' AND start_time IS NOT NULL AND finish_time IS NOT NULL)
        OR (status = 'FAILED' AND progress_stage = 'FAILED' AND finish_time IS NOT NULL)
        OR (status = 'CANCELLED' AND progress_stage = 'CANCELLED' AND finish_time IS NOT NULL)
    ),
    ADD INDEX idx_rr_status_id(status,id),
    ADD INDEX idx_rr_backup_time(backup_id,create_time,id);
