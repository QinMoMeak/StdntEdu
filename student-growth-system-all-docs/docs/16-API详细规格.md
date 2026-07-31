# API详细规格
JSON使用camelCase；数据库使用snake_case；BIGINT ID前端统一string；日期YYYY-MM-DD；时间ISO8601。

统一响应：
```json
{"code":"SUCCESS","message":"成功","data":{},"requestId":"uuid","timestamp":"2026-07-30T11:00:00+08:00"}
```

分页：
```json
{"items":[],"page":1,"pageSize":20,"total":0,"totalPages":0}
```

关键校验：
- 年级必须属于学段
- 成绩不得大于满分
- 排名不得大于人数
- 同一考试不得重复学科
- 错题来源只允许PRACTICE或EXAM
- 难度1至5
- 进度0至100
- AI枚举和知识点编码必须存在

关键错误码：VALIDATION_ERROR、RESOURCE_NOT_FOUND、DATA_VERSION_CONFLICT、GRADE_STAGE_MISMATCH、SCORE_EXCEEDS_FULL_SCORE、KNOWLEDGE_LOOP_DETECTED、AI_OUTPUT_INVALID、BACKUP_FAILED、RESTORE_FAILED。
