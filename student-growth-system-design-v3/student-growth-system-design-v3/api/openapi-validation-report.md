# OpenAPI V3.1 验证报告

## 结论

本轮审查完成，契约可作为 V3.1 阶段二后端开发基线。校验器、OpenAPI Generator validate、Java 模型试生成和 TypeScript Fetch 试生成均通过。生成目录仅写入系统临时目录，未提交到仓库。

## 结构统计

| 项目 | 结果 |
|---|---:|
| OpenAPI 版本 | 3.1.0 |
| 文件总行数 | 671 |
| 路径模板数量 | 84 |
| operation 数量 | 112 |
| operationId 总数 | 112 |
| operationId 唯一数 | 112 |
| 重复 operationId | 0 |
| Schema 数量 | 141 |
| Response 组件数量 | 67 |
| Parameter 组件数量 | 20 |
| 根节点重复 | 0 |

根节点 `openapi`、`info`、`servers`、`security`、`paths`、`components` 均各出现一次。所有路径变量均由 `required: true` 的 path parameter 声明；新增的 `GET /reviews/due` 已纳入到期复习闭环。

## 校验与生成

| 检查 | 结果 |
|---|---|
| `npx swagger-cli validate api/openapi.yaml` | 通过，exit 0 |
| `npx @redocly/cli lint api/openapi.yaml` | 通过，errors 0，warnings 0 |
| `npx @openapitools/openapi-generator-cli validate -i api/openapi.yaml` | 通过，exit 0 |
| Java 模型生成 | 通过，exit 0 |
| TypeScript Fetch 生成 | 通过，exit 0 |

Java 与 TypeScript 抽查模型均生成成功：`StudentDto`、`ExamDto`、`WrongQuestionDto`、`ResourceDto`、`AiExtractionTaskDto`、`StudyPlanDto`、`GrowthReportDto`、`ImportTaskDto`、`BackupDto`、`RestoreTaskDto`。其中基础实体名称通过 Generator 的 model name mapping 进行抽查，不改变契约名称或业务语义。

## 一致性审查

- 所有数据库 BIGINT 标识在 API Schema 中通过 `Id` 定义为 `string`；Java 抽查为 `String`，TypeScript 抽查为 `string`。
- 导入状态与 `import_task.status` CHECK 一致：`UPLOADED`、`VALIDATING`、`PREVIEW_READY`、`IMPORTING`、`SUCCESS`、`PARTIAL_SUCCESS`、`FAILED`、`CANCELLED`、`EXPIRED`。
- 导出状态与 `export_task.status` CHECK 一致：`PENDING`、`RUNNING`、`SUCCESS`、`FAILED`、`CANCELLED`、`EXPIRED`。
- 备份状态与 `backup_record.status` CHECK 一致；恢复状态与 `restore_record.status` CHECK 一致。
- 学习资源状态不包含 `DONE`；AI 任务状态 `REVIEW_REQUIRED` 与临时题目状态 `PENDING_REVIEW` 分离。
- 更新 DTO 对逻辑删除对象保留 `version`；关系表和历史表不强制暴露 version。
- 文件上传共 3 处使用 `multipart/form-data`，下载响应使用 binary Schema；附件、AI 识别和导入上传均具备文件类型及 413/415 响应约束。
- `apiKey`、备份密码和恢复密码均为 `writeOnly`，没有继承到普通响应 DTO。
- 统一 Envelope、Page、Error、错误 Response 组件和安全方案已复用；幂等确认和状态迁移操作包含 409。
- `attachment`、`entity_attachment` 等关系或基础设施表未单独暴露为业务 CRUD；其能力通过附件、资源、成长事件、导入导出和备份恢复接口闭环。

## 尚未解决的问题

无阻塞问题。OpenAPI Generator 7.24.0 对 OpenAPI 3.1 的提示是工具本身的 beta 提示，不影响本次 validate 或生成结果。真实 JWT 鉴权、文件生成、备份恢复执行和 AI 服务调用仍属于后续实现范围。
