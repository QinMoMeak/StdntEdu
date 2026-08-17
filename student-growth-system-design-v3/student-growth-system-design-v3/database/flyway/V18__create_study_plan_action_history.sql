USE student_growth;

CREATE TABLE study_plan_action_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    study_plan_id BIGINT NOT NULL,
    study_plan_task_id BIGINT NULL,
    action_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    reason VARCHAR(512) NULL,
    note VARCHAR(512) NULL,
    version_before INT NOT NULL,
    version_after INT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_spah_plan FOREIGN KEY (study_plan_id) REFERENCES study_plan(id),
    CONSTRAINT fk_spah_task FOREIGN KEY (study_plan_task_id) REFERENCES study_plan_task(id),
    CONSTRAINT chk_spah_action_type CHECK (
        action_type IN (
            'PLAN_ACTIVATE',
            'PLAN_PAUSE',
            'PLAN_COMPLETE',
            'PLAN_CANCEL',
            'TASK_COMPLETE',
            'TASK_SKIP'
        )
    ),
    CONSTRAINT chk_spah_task_scope CHECK (
        (action_type IN ('PLAN_ACTIVATE', 'PLAN_PAUSE', 'PLAN_COMPLETE', 'PLAN_CANCEL')
            AND study_plan_task_id IS NULL)
        OR
        (action_type IN ('TASK_COMPLETE', 'TASK_SKIP')
            AND study_plan_task_id IS NOT NULL)
    ),
    INDEX idx_spah_plan_time (study_plan_id, create_time, id),
    INDEX idx_spah_task_time (study_plan_task_id, create_time, id)
) ENGINE=InnoDB;
