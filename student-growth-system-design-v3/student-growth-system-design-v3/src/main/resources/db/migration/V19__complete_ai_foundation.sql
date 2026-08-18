USE student_growth;

CREATE TABLE ai_secret (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    secret_ref VARCHAR(255) NOT NULL,
    encrypted_value BLOB NOT NULL,
    nonce VARBINARY(32) NOT NULL,
    algorithm VARCHAR(32) NOT NULL DEFAULT 'AES-256-GCM',
    key_version INT NOT NULL DEFAULT 1,
    mask_suffix VARCHAR(16) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_ai_secret_ref UNIQUE (secret_ref),
    CONSTRAINT chk_ai_secret_algorithm CHECK (algorithm = 'AES-256-GCM'),
    CONSTRAINT chk_ai_secret_key_version CHECK (key_version >= 1),
    CONSTRAINT chk_ai_secret_nonce CHECK (OCTET_LENGTH(nonce) = 12)
) ENGINE=InnoDB;

ALTER TABLE ai_model
    DROP INDEX uk_ai_model,
    CHANGE COLUMN model_code model_name VARCHAR(128) NOT NULL,
    MODIFY COLUMN api_base_url VARCHAR(512) NOT NULL,
    ADD COLUMN model_type VARCHAR(32) NULL AFTER provider,
    ADD COLUMN protocol VARCHAR(32) NULL AFTER model_name,
    ADD COLUMN auth_type VARCHAR(32) NULL AFTER protocol,
    ADD COLUMN temperature DECIMAL(4,3) NULL AFTER timeout_seconds,
    ADD COLUMN max_tokens INT NULL AFTER temperature,
    ADD COLUMN remark TEXT NULL AFTER max_tokens,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER remark;

UPDATE ai_model
SET model_type = CASE WHEN supports_vision = 1 THEN 'MULTIMODAL' ELSE 'CHAT' END,
    protocol = CASE WHEN provider = 'OLLAMA' THEN 'OLLAMA' ELSE 'OPENAI_COMPATIBLE' END,
    auth_type = CASE
        WHEN provider = 'OLLAMA' AND api_key_ref IS NULL THEN 'NONE'
        ELSE 'BEARER_API_KEY'
    END;

ALTER TABLE ai_model
    MODIFY COLUMN model_type VARCHAR(32) NOT NULL,
    MODIFY COLUMN protocol VARCHAR(32) NOT NULL,
    MODIFY COLUMN auth_type VARCHAR(32) NOT NULL,
    ADD CONSTRAINT uk_ai_model UNIQUE (provider, model_name),
    ADD CONSTRAINT fk_ai_model_secret FOREIGN KEY (api_key_ref) REFERENCES ai_secret(secret_ref),
    ADD CONSTRAINT chk_ai_model_provider CHECK (provider IN ('DOUBAO','QWEN','DEEPSEEK','OPENAI','OLLAMA','CUSTOM')),
    ADD CONSTRAINT chk_ai_model_type CHECK (model_type IN ('CHAT','MULTIMODAL','EMBEDDING')),
    ADD CONSTRAINT chk_ai_model_protocol CHECK (protocol IN ('OPENAI_COMPATIBLE','OLLAMA')),
    ADD CONSTRAINT chk_ai_model_auth_type CHECK (auth_type IN ('NONE','BEARER_API_KEY')),
    ADD CONSTRAINT chk_ai_model_temperature CHECK (temperature IS NULL OR temperature BETWEEN 0 AND 2),
    ADD CONSTRAINT chk_ai_model_max_tokens CHECK (max_tokens IS NULL OR max_tokens >= 1);

ALTER TABLE ai_extraction_task
    ADD CONSTRAINT chk_ai_extraction_input_type CHECK (input_type IN ('IMAGE','PDF','MIXED'));

ALTER TABLE ai_extraction_question
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER user_modified,
    ADD CONSTRAINT uk_ai_extraction_question_task_id UNIQUE (task_id, id);

CREATE TABLE ai_extraction_confirmation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    result_json JSON NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_aic_task FOREIGN KEY (task_id) REFERENCES ai_extraction_task(id),
    CONSTRAINT uk_aic_task_idempotency UNIQUE (task_id, idempotency_key),
    CONSTRAINT chk_aic_idempotency_key CHECK (CHAR_LENGTH(idempotency_key) BETWEEN 8 AND 64),
    CONSTRAINT chk_aic_request_hash CHECK (request_hash REGEXP '^[0-9A-Fa-f]{64}$'),
    CONSTRAINT chk_aic_status CHECK (status IN ('PROCESSING','COMPLETED')),
    CONSTRAINT chk_aic_completed_result CHECK (status <> 'COMPLETED' OR result_json IS NOT NULL)
) ENGINE=InnoDB;

CREATE TABLE ai_extraction_confirmation_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    confirmation_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    wrong_question_id BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_aici_confirmation FOREIGN KEY (confirmation_id) REFERENCES ai_extraction_confirmation(id),
    CONSTRAINT fk_aici_question FOREIGN KEY (question_id) REFERENCES ai_extraction_question(id),
    CONSTRAINT fk_aici_wrong_question FOREIGN KEY (wrong_question_id) REFERENCES wrong_question(id),
    CONSTRAINT uk_aici_question UNIQUE (question_id),
    CONSTRAINT uk_aici_wrong_question UNIQUE (wrong_question_id)
) ENGINE=InnoDB;

ALTER TABLE ai_extraction_correction
    ADD CONSTRAINT fk_aec_task_question FOREIGN KEY (task_id, question_id)
        REFERENCES ai_extraction_question(task_id, id);

ALTER TABLE ai_extraction_question_knowledge
    ADD CONSTRAINT uk_aeqk_question_knowledge UNIQUE (extraction_question_id, knowledge_id);

ALTER TABLE ai_analysis
    ADD COLUMN estimated_cost DECIMAL(18,6) NULL AFTER duration_ms,
    ADD COLUMN currency_code CHAR(3) NULL AFTER estimated_cost,
    ADD CONSTRAINT chk_ai_analysis_estimated_cost CHECK (estimated_cost IS NULL OR estimated_cost >= 0),
    ADD CONSTRAINT chk_ai_analysis_currency CHECK (currency_code IS NULL OR currency_code REGEXP '^[A-Z]{3}$');
