USE student_growth;

CREATE TABLE entity_attachment(id BIGINT PRIMARY KEY AUTO_INCREMENT,entity_type VARCHAR(64) NOT NULL,entity_id BIGINT NOT NULL,attachment_id BIGINT NOT NULL,attachment_role VARCHAR(32),sort_order INT NOT NULL DEFAULT 0,create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),CONSTRAINT fk_ea_attachment FOREIGN KEY(attachment_id) REFERENCES attachment(id),UNIQUE KEY uk_ea(entity_type,entity_id,attachment_id,attachment_role)) ENGINE=InnoDB;

CREATE TABLE import_task(id BIGINT PRIMARY KEY AUTO_INCREMENT,task_code VARCHAR(64) NOT NULL UNIQUE,import_type VARCHAR(32) NOT NULL,status VARCHAR(32) NOT NULL,attachment_id BIGINT,total_rows INT NOT NULL DEFAULT 0,valid_rows INT NOT NULL DEFAULT 0,invalid_rows INT NOT NULL DEFAULT 0,preview_json JSON,error_report_attachment_id BIGINT,idempotency_key VARCHAR(64),expire_time DATETIME(3),create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),CONSTRAINT fk_it_attachment FOREIGN KEY(attachment_id) REFERENCES attachment(id),CONSTRAINT fk_it_error_attachment FOREIGN KEY(error_report_attachment_id) REFERENCES attachment(id),UNIQUE KEY uk_it_idempotency(idempotency_key)) ENGINE=InnoDB;

CREATE TABLE export_task(id BIGINT PRIMARY KEY AUTO_INCREMENT,task_code VARCHAR(64) NOT NULL UNIQUE,student_id BIGINT,export_types_json JSON NOT NULL,export_format VARCHAR(16) NOT NULL,status VARCHAR(32) NOT NULL,filter_json JSON,include_attachments TINYINT NOT NULL DEFAULT 0,include_deleted TINYINT NOT NULL DEFAULT 0,output_attachment_id BIGINT,error_message TEXT,expire_time DATETIME(3),create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),CONSTRAINT fk_et_student FOREIGN KEY(student_id) REFERENCES student(id),CONSTRAINT fk_et_output FOREIGN KEY(output_attachment_id) REFERENCES attachment(id)) ENGINE=InnoDB;

CREATE TABLE backup_record(id BIGINT PRIMARY KEY AUTO_INCREMENT,backup_code VARCHAR(64) NOT NULL UNIQUE,backup_type VARCHAR(32) NOT NULL,status VARCHAR(32) NOT NULL,file_name VARCHAR(255),storage_path VARCHAR(1024),file_size BIGINT,checksum VARCHAR(128),system_version VARCHAR(64),remark VARCHAR(512),error_message TEXT,create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),finish_time DATETIME(3)) ENGINE=InnoDB;

CREATE TABLE restore_record(id BIGINT PRIMARY KEY AUTO_INCREMENT,restore_code VARCHAR(64) NOT NULL UNIQUE,backup_id BIGINT NOT NULL,status VARCHAR(32) NOT NULL,progress_stage VARCHAR(64),progress_percent INT NOT NULL DEFAULT 0,pre_restore_backup_id BIGINT,error_message TEXT,create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),finish_time DATETIME(3),CONSTRAINT fk_rr_backup FOREIGN KEY(backup_id) REFERENCES backup_record(id),CONSTRAINT fk_rr_pre_backup FOREIGN KEY(pre_restore_backup_id) REFERENCES backup_record(id),CHECK(progress_percent BETWEEN 0 AND 100)) ENGINE=InnoDB;

CREATE TABLE system_config(id BIGINT PRIMARY KEY AUTO_INCREMENT,config_key VARCHAR(128) NOT NULL UNIQUE,config_value LONGTEXT,value_type VARCHAR(32) NOT NULL DEFAULT 'STRING',secret_flag TINYINT NOT NULL DEFAULT 0,description VARCHAR(512),update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)) ENGINE=InnoDB;

CREATE TABLE operation_log(id BIGINT PRIMARY KEY AUTO_INCREMENT,operation_type VARCHAR(64) NOT NULL,module_name VARCHAR(64) NOT NULL,target_type VARCHAR(64),target_id BIGINT,request_id VARCHAR(64),detail_json JSON,success_flag TINYINT NOT NULL DEFAULT 1,error_message TEXT,create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),INDEX idx_ol_module_time(module_name,create_time),INDEX idx_ol_target(target_type,target_id)) ENGINE=InnoDB;


ALTER TABLE wrong_question ADD CONSTRAINT chk_wq_status_v3 CHECK(status IN ('NEW','REVIEWING','MASTERED','ARCHIVED'));
ALTER TABLE ai_analysis ADD CONSTRAINT chk_aa_status_v3 CHECK(status IN ('PENDING','RUNNING','REVIEW_REQUIRED','SUCCESS','FAILED','CANCELLED','EXPIRED'));
ALTER TABLE ai_extraction_task ADD CONSTRAINT chk_aet_status_v3 CHECK(status IN ('PENDING','RUNNING','REVIEW_REQUIRED','SUCCESS','FAILED','CANCELLED','EXPIRED'));
ALTER TABLE ai_extraction_question ADD CONSTRAINT chk_aeq_status_v3 CHECK(status IN ('PENDING_REVIEW','CONFIRMED','IGNORED','SAVED','INVALID'));
ALTER TABLE study_plan ADD CONSTRAINT chk_sp_status_v3 CHECK(status IN ('DRAFT','ACTIVE','PAUSED','COMPLETED','CANCELLED','EXPIRED'));
ALTER TABLE study_plan_task ADD CONSTRAINT chk_spt_status_v3 CHECK(status IN ('TODO','IN_PROGRESS','COMPLETED','SKIPPED','CANCELLED'));
ALTER TABLE recommendation ADD CONSTRAINT chk_rec_status_v3 CHECK(status IN ('ACTIVE','ACCEPTED','DISMISSED','COMPLETED','EXPIRED'));
ALTER TABLE growth_report ADD CONSTRAINT chk_gr_status_v3 CHECK(status IN ('PENDING','RUNNING','SUCCESS','FAILED','CANCELLED'));
ALTER TABLE import_task ADD CONSTRAINT chk_it_status_v3 CHECK(status IN ('UPLOADED','VALIDATING','PREVIEW_READY','IMPORTING','SUCCESS','PARTIAL_SUCCESS','FAILED','CANCELLED','EXPIRED'));
ALTER TABLE export_task ADD CONSTRAINT chk_et_status_v3 CHECK(status IN ('PENDING','RUNNING','SUCCESS','FAILED','CANCELLED','EXPIRED'));
ALTER TABLE backup_record ADD CONSTRAINT chk_br_status_v3 CHECK(status IN ('PENDING','RUNNING','SUCCESS','FAILED','DELETED','EXPIRED'));
ALTER TABLE restore_record ADD CONSTRAINT chk_rr_status_v3 CHECK(status IN ('PENDING','CREATING_SNAPSHOT','VALIDATING','RESTORING_DATABASE','RESTORING_FILES','MIGRATING','VERIFYING','SUCCESS','FAILED','ROLLED_BACK'));
