USE student_growth;

CREATE TABLE student_resource_assignment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING',
    assigned_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    remark VARCHAR(512) NULL,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_sra_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_sra_resource FOREIGN KEY (resource_id) REFERENCES learning_resource(id),
    CONSTRAINT uk_sra_student_resource UNIQUE (student_id, resource_id),
    CONSTRAINT chk_sra_status CHECK (status IN ('WAITING', 'LEARNING', 'COMPLETED', 'REVIEW', 'ARCHIVED')),
    INDEX idx_sra_student_status (student_id, status, assigned_time, id)
) ENGINE=InnoDB;
