USE student_growth;

ALTER TABLE study_log
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER deleted;
