USE student_growth;
INSERT INTO system_config(config_key,config_value,value_type,description) VALUES ('mastery.algorithm.version','1.0','STRING','掌握度算法版本'),('mastery.score_rate.correct_min','0.80','DECIMAL','成绩正确分类最低得分率'),('mastery.score_rate.partial_min','0.60','DECIMAL','成绩部分正确分类最低得分率'),('mastery.time_decay.enabled','false','BOOLEAN','掌握度时间衰减开关');
