# OpenAPI V3.1.3 验证报告

## V3.1.3 Mastery 兼容修订

V3.1.3 只修复掌握度输出完整性和生成接口参数完整性：

1. `MasteryDto` 新增可选只读字段 `evidenceCount` 和 `nextReviewTime`。既有字段、字段类型、枚举和业务语义均未改变。
2. `GET /knowledge/mastery/{knowledgeId}/history` 的 `knowledgeId` 从 Path Item 参数调整为 `listMasteryHistory` 操作的显式必填 path 参数；URL 和 operationId 均未改变。
3. 生成的 Java Spring 方法现在显式接收 `String knowledgeId`，实现 Controller 已删除从 `HttpServletRequest` URI template variables 手工取参的 workaround。
4. Java 模型将 `evidenceCount` 生成为 `Integer`、`nextReviewTime` 生成为 `OffsetDateTime`；TypeScript Fetch 将其生成为可选只读 `number` 和 `Date | null`。所有 ID 类型仍为 Java `String` / TypeScript `string`。

Mastery Algorithm 保持 V1.0，Flyway 最终版本保持 V14，数据库结构和算法实现均无变化。

## V3.1.3 校验与生成

- `npx swagger-cli validate api/openapi.yaml`：通过，exit 0。
- `npx @redocly/cli lint api/openapi.yaml`：通过，errors 0、warnings 0。
- `npx @openapitools/openapi-generator-cli validate -i api/openapi.yaml`：Node 24.18.0 下 wrapper 在进入校验前启动失败；改用项目同版本 Generator CLI 7.10.0 的 Maven 执行入口后 validate 通过，exit 0。未使用模型提示不影响解析或生成。
- Java 模型生成：通过，exit 0；Spring 接口参数为 `knowledgeId`、`studentId`、`page`、`pageSize`。
- TypeScript Fetch 生成：通过，exit 0；请求参数显式包含 `knowledgeId`。
- `mvnw.cmd generate-sources`：通过，生成接口与 Controller 适配一致。
- `mvnw.cmd clean test`：通过，tests 111、failures 0、errors 0、skipped 0。
- `mvnw.cmd clean package`：通过，tests 111、failures 0、errors 0、skipped 0，打包成功。
- Testcontainers MySQL 8 从空数据库成功执行 Flyway V1-V14，最终版本为 v14；阶段六 26 项测试全部通过，算法预期无变化。

试生成目录位于 `target`，不提交到 Git。

## V3.1.2 掌握度契约修订

V3.1.2 只修复阶段六掌握度记录唯一定位缺口。`unlockKnowledgeMastery` 保留原 path、
`knowledgeId` 和 operationId，新增必填 query 参数 `studentId`；Java Spring 接口将两个 ID
均生成为 `String`，TypeScript Fetch 将两个 ID 均生成为 `string`。

`adjustKnowledgeMastery` 的既有 `MasteryAdjustRequest` 已包含必填 `studentId`，因此没有增加重复参数；说明已冻结人工调整必须锁定结果、自动证据不得覆盖分数的语义。没有修改其他业务 API、已有字段类型、路径或 operationId。

## V3.1.2 校验结论

- `npx swagger-cli validate api/openapi.yaml`：通过，exit 0。
- `npx @redocly/cli lint api/openapi.yaml`：通过，errors 0、warnings 0。
- `npx @openapitools/openapi-generator-cli validate -i api/openapi.yaml`：Node 24.18.0 下 wrapper 退出 1。
- OpenAPI Generator CLI 7.10.0 JAR validate：通过，exit 0；仅有 4 个既有未使用 Schema 建议。
- Java 模型生成：通过，exit 0。
- TypeScript Fetch 生成：通过，exit 0。
- Maven Spring Generator 7.10.0：生成并编译通过。

试生成目录位于 `target`，不提交到 Git。

## Flyway V14 与回归结果

Testcontainers MySQL 8 从空数据库先迁移 V1-V13，再执行 V14，最终版本为 v14。V14 前后业务表均为 42 张，全部业务表列结构签名完全一致，证明 V14 没有新增表或修改既有表结构。

`system_config` 在 V13 后为 27 项，V14 后为 31 项；新增的算法版本、两个成绩分类阈值和时间衰减开关均按预期写入，`config_key` 无重复。设计基线和运行时目录中的两份 V14 文件 SHA-256 相同。

`mvnw.cmd clean test` 与 `mvnw.cmd clean package` 均通过：tests 65、failures 0、errors 0、skipped 0；OpenAPI Generator、Testcontainers MySQL 8 和 Flyway V1-V14 均成功。

## V3.1.1 兼容性修订范围

考试与成绩契约新增 `ScoreKnowledgeInput`、`ScoreKnowledgeDto`、`SubjectScoreDto`，并为 `SubjectScore` 增加 `classSize`、`gradeSize`、`knowledgeScores`。`Exam` 响应新增只读 `totalScore`、`totalFullScore`、`totalScoreRate`。没有删除或重命名路径、operationId、既有字段或枚举；数据库和 Flyway V1-V13 未修改。

## V3.1.1 生成抽查

Java 和 TypeScript Fetch 均成功生成。抽查结果如下：

- `ScoreKnowledgeInput.knowledgeId` 生成为 Java `String`、TypeScript `string`。
- `SubjectScore.classSize`、`SubjectScore.gradeSize` 生成为 Java `Integer`、TypeScript `number`，`knowledgeScores` 生成为 `List<ScoreKnowledgeInput>` / `Array<ScoreKnowledgeInput>`。
- `Exam.subjects` 在响应模型生成成 `List<SubjectScoreDto>`，知识点响应使用 `ScoreKnowledgeDto`。
- `Exam.totalScore`、`totalFullScore`、`totalScoreRate` 生成成 Java `BigDecimal`、TypeScript `readonly number`。
- OpenAPI Generator 对 OpenAPI 3.1 输出 beta 提示，以及对既有 `Error`、`ResourceCreateRequest`、`ResourceUpdateRequest`、`AiExtractionConfirmResultDto` 输出未使用 Schema 建议；均非解析或生成失败。

## 结论

本轮审查完成，契约可作为 V3.1.3 阶段七开始前的新契约基线。校验器、OpenAPI Generator validate、Java 模型试生成和 TypeScript Fetch 试生成均通过。生成目录仅写入 `target`，不提交到仓库。

## 结构统计

| 项目 | 结果 |
|---|---:|
| OpenAPI 规范版本 | 3.1.0 |
| 契约基线版本 | 3.1.3 |
| 文件总行数 | 673 |
| 路径模板数量 | 84 |
| operation 数量 | 112 |
| operationId 总数 | 112 |
| operationId 唯一数 | 112 |
| 重复 operationId | 0 |
| Schema 数量 | 144 |
| Response 组件数量 | 67 |
| Parameter 组件数量 | 20 |
| 根节点重复 | 0 |

根节点 `openapi`、`info`、`servers`、`security`、`paths`、`components` 均各出现一次。所有路径变量均由 `required: true` 的 path parameter 声明；新增的 `GET /reviews/due` 已纳入到期复习闭环。

## 校验与生成

| 检查 | 结果 |
|---|---|
| `npx swagger-cli validate api/openapi.yaml` | 通过，exit 0 |
| `npx @redocly/cli lint api/openapi.yaml` | 通过，errors 0，warnings 0 |
| `npx @openapitools/openapi-generator-cli validate -i api/openapi.yaml` | Node 24.18.0 启动器在命令转发时退出 1；已改用其缓存的 Generator CLI 7.10.0 JAR验证通过 |
| OpenAPI Generator CLI 7.10.0 JAR validate | 通过，exit 0；仅报告 4 个既有未使用 Schema 建议 |
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

无阻塞问题。OpenAPI Generator 对 OpenAPI 3.1 的 beta 提示及既有未使用 Schema 建议不影响 validate、生成或 Maven 编译结果。Node 24 下 npx wrapper 仍无法稳定启动，已通过同版本 Generator CLI 7.10.0 的 Maven 执行入口完成 validate 和试生成。真实 JWT 鉴权仍属于后续阶段。
