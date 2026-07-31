CREATE DATABASE IF NOT EXISTS student_growth DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE student_growth;
-- 本文件是设计基线快照。正式开发时请由Codex按docs/06和docs/18-19拆分为Flyway迁移。
-- 关键表清单：
-- stage, grade, subject, student, academic_term, dict_type, dict_item,
-- knowledge_node, knowledge_relation, exam, score_record, score_knowledge,
-- wrong_question, wrong_question_knowledge, wrong_review,
-- student_mastery, mastery_history, learning_resource,
-- learning_resource_knowledge, resource_history, study_log, growth_event,
-- ai_model, prompt_template, ai_analysis, ai_extraction_task,
-- ai_extraction_file, ai_extraction_question,
-- ai_extraction_question_knowledge, ai_extraction_correction,
-- attachment, entity_attachment, backup_record, system_config, operation_log.
