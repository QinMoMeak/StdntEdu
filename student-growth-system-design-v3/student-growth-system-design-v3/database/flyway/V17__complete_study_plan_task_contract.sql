USE student_growth;

ALTER TABLE study_plan_task
    ADD COLUMN exam_id BIGINT NULL AFTER knowledge_id,
    ADD COLUMN actual_duration_seconds INT NULL AFTER expected_duration_seconds,
    ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER remark,
    ADD CONSTRAINT fk_spt_exam FOREIGN KEY (exam_id) REFERENCES exam(id),
    ADD CONSTRAINT chk_spt_actual_duration CHECK (
        actual_duration_seconds IS NULL OR actual_duration_seconds >= 0
    ),
    ADD CONSTRAINT chk_spt_task_type CHECK (
        task_type IN (
            'WRONG_QUESTION_REVIEW',
            'RESOURCE_LEARNING',
            'KNOWLEDGE_PRACTICE',
            'EXAM_REVIEW',
            'READING',
            'OTHER'
        )
    );
