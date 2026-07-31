USE student_growth;
INSERT INTO dict_type(dict_code,dict_name,description) VALUES
('wrong_question_error_type','错题错误类型','错题原因分类'),
('question_type','题型','通用题型'),
('growth_event_type','成长事件类型','成长时间轴事件'),
('learning_resource_source','学习资源来源','资源来源'),
('custom_tag','自定义标签','用户自定义标签');

INSERT INTO dict_item(dict_type_id,item_code,item_label,sort_order,system_flag)
SELECT id,'KNOWLEDGE_GAP','知识不会',10,1 FROM dict_type WHERE dict_code='wrong_question_error_type'
UNION ALL SELECT id,'CONCEPT_CONFUSION','概念混淆',20,1 FROM dict_type WHERE dict_code='wrong_question_error_type'
UNION ALL SELECT id,'FORMULA_ERROR','公式错误',30,1 FROM dict_type WHERE dict_code='wrong_question_error_type'
UNION ALL SELECT id,'CALCULATION_ERROR','计算错误',40,1 FROM dict_type WHERE dict_code='wrong_question_error_type'
UNION ALL SELECT id,'READING_ERROR','审题错误',50,1 FROM dict_type WHERE dict_code='wrong_question_error_type'
UNION ALL SELECT id,'METHOD_ERROR','方法错误',60,1 FROM dict_type WHERE dict_code='wrong_question_error_type'
UNION ALL SELECT id,'UNIT_ERROR','单位错误',70,1 FROM dict_type WHERE dict_code='wrong_question_error_type'
UNION ALL SELECT id,'CARELESS','粗心',80,1 FROM dict_type WHERE dict_code='wrong_question_error_type'
UNION ALL SELECT id,'UNKNOWN','原因未确认',90,1 FROM dict_type WHERE dict_code='wrong_question_error_type'
UNION ALL SELECT id,'OTHER','其他',999,1 FROM dict_type WHERE dict_code='wrong_question_error_type';

INSERT INTO dict_item(dict_type_id,item_code,item_label,sort_order,system_flag)
SELECT id,'SINGLE_CHOICE','单项选择题',10,1 FROM dict_type WHERE dict_code='question_type'
UNION ALL SELECT id,'MULTIPLE_CHOICE','多项选择题',20,1 FROM dict_type WHERE dict_code='question_type'
UNION ALL SELECT id,'TRUE_FALSE','判断题',30,1 FROM dict_type WHERE dict_code='question_type'
UNION ALL SELECT id,'FILL_BLANK','填空题',40,1 FROM dict_type WHERE dict_code='question_type'
UNION ALL SELECT id,'CALCULATION','计算题',50,1 FROM dict_type WHERE dict_code='question_type'
UNION ALL SELECT id,'APPLICATION','应用题',60,1 FROM dict_type WHERE dict_code='question_type'
UNION ALL SELECT id,'SHORT_ANSWER','简答题',70,1 FROM dict_type WHERE dict_code='question_type'
UNION ALL SELECT id,'ESSAY','作文或长答题',80,1 FROM dict_type WHERE dict_code='question_type'
UNION ALL SELECT id,'READING','阅读理解',90,1 FROM dict_type WHERE dict_code='question_type'
UNION ALL SELECT id,'LISTENING','听力题',100,1 FROM dict_type WHERE dict_code='question_type'
UNION ALL SELECT id,'DRAWING','作图题',110,1 FROM dict_type WHERE dict_code='question_type'
UNION ALL SELECT id,'EXPERIMENT','实验题',120,1 FROM dict_type WHERE dict_code='question_type'
UNION ALL SELECT id,'COMPREHENSIVE','综合题',130,1 FROM dict_type WHERE dict_code='question_type'
UNION ALL SELECT id,'OTHER','其他',999,1 FROM dict_type WHERE dict_code='question_type';

INSERT INTO dict_item(dict_type_id,item_code,item_label,sort_order,system_flag)
SELECT id,'AWARD','获奖',10,1 FROM dict_type WHERE dict_code='growth_event_type'
UNION ALL SELECT id,'READING','阅读成果',20,1 FROM dict_type WHERE dict_code='growth_event_type'
UNION ALL SELECT id,'EXAM','重要考试',30,1 FROM dict_type WHERE dict_code='growth_event_type'
UNION ALL SELECT id,'ACTIVITY','活动',40,1 FROM dict_type WHERE dict_code='growth_event_type'
UNION ALL SELECT id,'INTEREST','兴趣发展',50,1 FROM dict_type WHERE dict_code='growth_event_type'
UNION ALL SELECT id,'MILESTONE','学习里程碑',60,1 FROM dict_type WHERE dict_code='growth_event_type'
UNION ALL SELECT id,'SUMMARY','阶段总结',70,1 FROM dict_type WHERE dict_code='growth_event_type'
UNION ALL SELECT id,'HABIT','习惯养成',80,1 FROM dict_type WHERE dict_code='growth_event_type'
UNION ALL SELECT id,'OTHER','其他',999,1 FROM dict_type WHERE dict_code='growth_event_type';

INSERT INTO dict_item(dict_type_id,item_code,item_label,sort_order,system_flag)
SELECT id,'BILIBILI','Bilibili',10,1 FROM dict_type WHERE dict_code='learning_resource_source'
UNION ALL SELECT id,'CLOUD_DRIVE','网盘',20,1 FROM dict_type WHERE dict_code='learning_resource_source'
UNION ALL SELECT id,'LOCAL','本地文件',30,1 FROM dict_type WHERE dict_code='learning_resource_source'
UNION ALL SELECT id,'WEB','普通网页',40,1 FROM dict_type WHERE dict_code='learning_resource_source'
UNION ALL SELECT id,'SMART_EDUCATION','国家智慧教育平台',50,1 FROM dict_type WHERE dict_code='learning_resource_source'
UNION ALL SELECT id,'MOOC','慕课平台',60,1 FROM dict_type WHERE dict_code='learning_resource_source'
UNION ALL SELECT id,'OTHER','其他',999,1 FROM dict_type WHERE dict_code='learning_resource_source';
