# API设计
前缀：/api/v1。

基础：GET /stages、/grades、/subjects、/students、/academic-terms、/dictionaries/{type}。
成绩：GET /scores；POST/GET/PUT/DELETE /exams；GET /scores/trends；POST /scores/import。
错题：GET/POST /wrong-questions；GET/PUT/DELETE /wrong-questions/{id}；POST /wrong-questions/{id}/reviews。
知识：GET /knowledge/tree；POST/PUT /knowledge/nodes；GET /knowledge/mastery。
资源：GET/POST /resources；PUT /resources/{id}；POST /resources/{id}/history。
AI：GET/POST /ai/models；POST /ai/wrong-question-extractions；GET任务；POST确认。
备份：GET/POST /backups；POST /backups/{id}/restore。
