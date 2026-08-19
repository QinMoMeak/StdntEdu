USE student_growth;

CREATE TEMPORARY TABLE v20_requires_empty_ai_analysis (
    marker TINYINT PRIMARY KEY
);
INSERT INTO v20_requires_empty_ai_analysis (marker) VALUES (1);
INSERT INTO v20_requires_empty_ai_analysis (marker)
SELECT 1 FROM ai_analysis LIMIT 1;
DROP TEMPORARY TABLE v20_requires_empty_ai_analysis;

ALTER TABLE ai_analysis
    ADD COLUMN input_json JSON NULL AFTER input_summary,
    ADD COLUMN idempotency_key VARCHAR(64) NOT NULL AFTER input_json,
    ADD COLUMN request_hash CHAR(64) NOT NULL AFTER idempotency_key,
    ADD COLUMN started_time DATETIME(3) NULL AFTER duration_ms,
    ADD COLUMN finished_time DATETIME(3) NULL AFTER started_time,
    ADD CONSTRAINT uk_aa_idempotency UNIQUE (student_id, business_type, idempotency_key),
    ADD CONSTRAINT chk_aa_business_type_v20 CHECK (business_type = 'STUDY_PLAN_GENERATION'),
    ADD CONSTRAINT chk_aa_study_plan_identity_v20 CHECK (
        business_type <> 'STUDY_PLAN_GENERATION'
        OR (student_id IS NOT NULL AND business_id IS NULL AND prompt_template_id IS NULL
            AND input_json IS NOT NULL)
    ),
    ADD CONSTRAINT chk_aa_idempotency_key_v20 CHECK (CHAR_LENGTH(idempotency_key) BETWEEN 8 AND 64),
    ADD CONSTRAINT chk_aa_request_hash_v20 CHECK (request_hash REGEXP '^[0-9A-Fa-f]{64}$'),
    ADD CONSTRAINT chk_aa_input_json_v20 CHECK (
        JSON_UNQUOTE(JSON_EXTRACT(input_json, '$.schemaVersion')) = '1'
        AND JSON_UNQUOTE(JSON_EXTRACT(input_json, '$.promptVersion')) = 'study-plan-generation-v1'
        AND JSON_TYPE(JSON_EXTRACT(input_json, '$.request')) = 'OBJECT'
    ),
    ADD CONSTRAINT chk_aa_status_subset_v20 CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED')),
    ADD CONSTRAINT chk_aa_lifecycle_v20 CHECK (
        (status = 'PENDING'
            AND started_time IS NULL AND finished_time IS NULL AND duration_ms IS NULL
            AND result_json IS NULL AND error_code IS NULL AND error_message IS NULL)
        OR (status = 'RUNNING'
            AND started_time IS NOT NULL AND finished_time IS NULL AND duration_ms IS NULL
            AND result_json IS NULL AND error_code IS NULL AND error_message IS NULL)
        OR (status = 'SUCCESS'
            AND started_time IS NOT NULL AND finished_time IS NOT NULL AND duration_ms IS NOT NULL
            AND result_json IS NOT NULL AND error_code IS NULL AND error_message IS NULL)
        OR (status = 'FAILED'
            AND started_time IS NOT NULL AND finished_time IS NOT NULL AND duration_ms IS NOT NULL
            AND result_json IS NULL AND error_code IS NOT NULL AND error_message IS NOT NULL)
    ),
    ADD CONSTRAINT chk_aa_timing_v20 CHECK (
        (duration_ms IS NULL OR duration_ms >= 0)
        AND (finished_time IS NULL OR started_time IS NULL OR finished_time >= started_time)
    ),
    ADD CONSTRAINT chk_aa_token_usage_v20 CHECK (
        (prompt_tokens IS NULL OR prompt_tokens >= 0)
        AND (completion_tokens IS NULL OR completion_tokens >= 0)
    ),
    ADD CONSTRAINT chk_aa_cost_v1_v20 CHECK (estimated_cost IS NULL AND currency_code IS NULL);
