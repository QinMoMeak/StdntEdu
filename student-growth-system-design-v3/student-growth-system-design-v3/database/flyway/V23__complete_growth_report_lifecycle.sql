USE student_growth;

ALTER TABLE growth_report
    ADD COLUMN request_json JSON NULL AFTER status,
    ADD COLUMN source_report_id BIGINT NULL AFTER request_json,
    ADD COLUMN snapshot_schema_version INT NOT NULL DEFAULT 1 AFTER source_report_id,
    ADD COLUMN generation_version VARCHAR(32) NOT NULL DEFAULT '1.0' AFTER snapshot_schema_version,
    ADD COLUMN progress_percent INT NOT NULL DEFAULT 0 AFTER generation_version,
    ADD COLUMN cancel_requested TINYINT NOT NULL DEFAULT 0 AFTER progress_percent,
    ADD COLUMN error_code VARCHAR(64) NULL AFTER content_markdown,
    ADD COLUMN error_message TEXT NULL AFTER error_code,
    ADD COLUMN start_time DATETIME(3) NULL AFTER error_message,
    ADD COLUMN finish_time DATETIME(3) NULL AFTER start_time;

UPDATE growth_report
SET generation_type = 'DETERMINISTIC',
    request_json = JSON_OBJECT(
        'schemaVersion', 1,
        'generationVersion', '1.0',
        'studentId', CAST(student_id AS CHAR),
        'reportType', report_type,
        'title', title,
        'startDate', DATE_FORMAT(start_date, '%Y-%m-%d'),
        'endDate', DATE_FORMAT(end_date, '%Y-%m-%d')
    ),
    progress_percent = CASE WHEN status = 'SUCCESS' THEN 100 ELSE 0 END,
    start_time = CASE WHEN status IN ('RUNNING','SUCCESS') THEN create_time ELSE NULL END,
    finish_time = CASE WHEN status IN ('SUCCESS','FAILED','CANCELLED') THEN update_time ELSE NULL END,
    cancel_requested = CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END;

ALTER TABLE growth_report
    MODIFY COLUMN request_json JSON NOT NULL,
    ADD CONSTRAINT fk_gr_source_report FOREIGN KEY(source_report_id) REFERENCES growth_report(id),
    ADD CONSTRAINT chk_gr_report_type_v23 CHECK(report_type IN ('DAILY','WEEKLY','MONTHLY','TERM','YEARLY','CUSTOM')),
    ADD CONSTRAINT chk_gr_generation_type_v23 CHECK(generation_type = 'DETERMINISTIC'),
    ADD CONSTRAINT chk_gr_snapshot_version_v23 CHECK(snapshot_schema_version = 1),
    ADD CONSTRAINT chk_gr_progress_v23 CHECK(progress_percent BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_gr_lifecycle_v23 CHECK(
        (status = 'PENDING' AND start_time IS NULL AND finish_time IS NULL)
        OR (status = 'RUNNING' AND start_time IS NOT NULL AND finish_time IS NULL)
        OR (status = 'SUCCESS' AND start_time IS NOT NULL AND finish_time IS NOT NULL AND progress_percent = 100)
        OR (status = 'FAILED' AND finish_time IS NOT NULL)
        OR (status = 'CANCELLED' AND finish_time IS NOT NULL)
    ),
    ADD INDEX idx_gr_student_time(student_id,create_time,id),
    ADD INDEX idx_gr_status_id(status,id),
    ADD INDEX idx_gr_source_status(source_report_id,status,id);
