# OpenAPI V3.5.0 验证报告

## V3.5.0 AI 基础安全与数据闭环

V3.5.0 将阶段十 AI 实现所需的模型能力、协议、认证、Secret 生命周期、乐观锁、识别确认幂等和异步计划生成语义固定为可生成契约，并将数据库基线升级到 Flyway V19。路径和 operationId 均未增加、删除或重命名；本次是 AI 子系统的大版本契约闭环，不是兼容性 patch。

- `AiModelCreateRequest`、`AiModelUpdateRequest` 和 `AiModelDto` 对齐 `provider`、`modelName`、`modelType`、`protocol`、`authType`、`baseUrl`、`temperature`、`maxTokens`、`remark` 和能力标志。更新与启停均要求 version，启停请求生成为 `AiModelStatusChangeRequest`。
- 完整 `apiKey` 只存在于 create/update 的 writeOnly 输入。响应只返回 `apiKeyConfigured` 和 `apiKeyMasked`；update 的 `clearApiKey` 固定保留、替换、清除三种生命周期语义。
- `testAiModelConnection` 不再生成无类型 Object body，仅接收 path `modelId` 并返回 `AiModelConnectionTestDto`；操作固定为只读且必须脱敏。
- `AiExtractionQuestionDto.version` 为必填只读字段，更新请求 version 必填；`AiInputType` 固定为 IMAGE、PDF、MIXED，并由服务端依据上传文件推导。
- confirm 固定为 all-or-nothing 本地事务；`(taskId, Idempotency-Key)` 和规范化请求 SHA-256 共同决定重放或 `409 IDEMPOTENCY_CONFLICT`，不允许 partial success。
- `AiAnalysisDto.status` 复用与数据库 CHECK 一致的 `AiTaskStatus`。成功值沿用冻结编码 `SUCCESS`，不引入 `COMPLETED`；成本使用非负 `DECIMAL(18,6)` 和可空三位大写货币代码。
- `generateStudyPlan` 保留原 URL、operationId 和 HTTP 202，但成功响应改为 `AiAnalysisDto`。未来实现只创建 PENDING analysis；成功落库时创建 DRAFT plan 并将 analysis 改为 SUCCESS，失败改为 FAILED，不创建占位计划。

## V3.5.0 校验与生成结果

- OpenAPI 文件：3.1.0 规范，`info.version=3.5.0`，709 行。
- 结构统计：86 个路径模板、116 个操作、156 个 Schema、70 个 Response 组件；operationId 为 116/116/0，Path Variable 缺口为 0。
- Swagger CLI：通过，exit 0。
- Redocly recommended lint：通过，errors 0、warnings 0。
- Node 24.18.0 下的 `npx @openapitools/openapi-generator-cli` wrapper 仍在命令转发前退出 1。使用 Java 21 与本地 Maven 仓库中的 OpenAPI Generator CLI 7.10.0 JAR执行同等 validate，结果通过；仅报告 4 个既有未使用模型建议：`Error`、`ResourceCreateRequest`、`ResourceUpdateRequest`、`AiExtractionConfirmResultDto`。
- Java client model generation、TypeScript Fetch generation 及 Maven Spring generation 均通过。Java BIGINT ID 生成为 `String`，TypeScript 生成为 `string`；version 生成为 Java `Integer` / TypeScript `number`。
- 生成抽查确认：`AiModelDto` 不含完整 `apiKey`；update 的 `apiKey` 保持 writeOnly，`clearApiKey` 为 Boolean；连接测试、状态变更、临时题 version、输入类型和分析状态均生成明确类型。

## Flyway V19 验证结果

- V19 新增 `ai_secret`、`ai_extraction_confirmation`、`ai_extraction_confirmation_item`，并只补齐既有 AI 表的字段与完整性约束；业务表由 44 增至 47，`system_config` 保持 31。
- `ai_secret` 使用 `secret_ref` 唯一键、加密值、12-byte nonce、固定 `AES-256-GCM`、`key_version>=1` 和非敏感 `mask_suffix`。`ai_model.api_key_ref` 已通过外键引用 `ai_secret.secret_ref`。
- `ai_model.version` 使用 `INT NOT NULL DEFAULT 0`，参考 `learning_resource`、`study_log` 和 `student_resource_assignment` 的非 StudyPlan 乐观锁规范。
- Provider、ModelType、Protocol、AuthType、temperature、maxTokens、inputType、confirmation status、request hash、成本和货币代码均有数据库 CHECK；临时题 version、确认唯一键、一对一映射、correction 复合外键和知识点去重约束均已验证。
- 两份 V19 SHA-256 均为 `C369D3D17A46ED3ECDD512C8F42B63FA49EAC584F1850F7D981BAB8133D081F9`，内容逐字一致。`schema-full.sql` 已同步 V19 后完整 AI 结构，非 AI 表结构未变化。
- Testcontainers MySQL 8 从空库成功执行 V1-V19，最终版本 v19；独立 V18→V19 存量 AI 数据升级验证保留原主键和业务字段，并正确回填 modelType、protocol、authType 与 version。V1-V18 已应用 checksum 保持不变。
- `mvnw.cmd clean test`：通过，tests 192、failures 0、errors 0、skipped 0。
- `mvnw.cmd clean package`：通过，tests 192、failures 0、errors 0、skipped 0，可执行 JAR 打包成功。
- 由于阶段七测试同时使用 JVM `LocalDate.now()` 和 MySQL `CURRENT_TIMESTAMP`，上海时区凌晨会跨 UTC 日期。回归通过时仅对测试连接设置 `sessionVariables time_zone=+08:00`，未修改应用配置或源码；该测试时区耦合留待后续独立治理。

## 尚未实现与后续事项

- 本轮未实现 AiModelService、Provider Adapter、Secret 加解密代码、Extraction/Analysis worker、`generateStudyPlan` worker 或任何外部模型调用。
- 阶段十实现必须统一清理 `apiKey`、Authorization、token、secret、password，包括 `fieldErrors.rejectedValue`、operation log、应用日志及 Provider 异常；当前 `GlobalExceptionHandler` 的回显风险仍需处理。
- Spring multipart 大小必须在 AI 业务实现阶段显式配置；本轮未修改 application YAML。
- AI analysis 历史组合索引仅记录为后续 EXPLAIN 驱动的性能候选，本轮未增加纯性能索引。

## V3.4.1 StudyPlan 操作历史持久化语义

V3.4.1 不改变路径、operationId、请求字段结构、字段类型或状态机，只为三个既有命令字段冻结持久化语义，并将数据库基线升级为 Flyway V18：

- `StudyPlanStatusChangeRequest.reason` 写入不可变 `study_plan_action_history.reason`，适用于计划激活、暂停、完成和取消，不覆盖 `study_plan.description`。
- `SkipStudyPlanTaskRequest.reason` 写入 `TASK_SKIP` 历史的 `reason`，不覆盖 `study_plan_task.remark`。
- `CompleteStudyPlanTaskRequest.note` 写入 `TASK_COMPLETE` 历史的 `note`，不覆盖 `study_plan_task.remark`。
- 状态更新与历史 INSERT 必须处于同一事务；历史写入失败时状态更新回滚，stale version、非法状态机或其他失败命令不写历史。
- `version_before` 和 `version_after` 保存成功状态变化前后的版本快照，业务实现必须保证 `version_after = version_before + 1`。

V18 新增 `study_plan_action_history`，不使用 deleted 或 version，不提供修改、删除或公开查询 API。`PLAN_*` 历史要求 `study_plan_task_id IS NULL`，`TASK_*` 历史要求其非空，该规则由数据库 CHECK 固定。新表同时提供计划维度和任务维度的时间顺序索引；V1-V17 保持冻结。

## V3.4.1 校验、生成与迁移结果

- Swagger CLI：通过，exit 0。
- Redocly：通过，errors 0、warnings 0。
- `npx @openapitools/openapi-generator-cli` 在 Node 24.18.0 下仍于命令转发前退出 1；按既有治理方案使用 Java 21 和缓存的 OpenAPI Generator CLI 7.10.0 JAR完成 validate，结果通过，仅报告 4 个既有未使用 Schema 建议。
- Java model generation 与 TypeScript Fetch generation：均通过，exit 0；Maven Spring Generator 7.10.0 生成并编译通过。
- 结构统计：86 个路径模板、116 个操作、150 个 Schema；operationId 为 116/116/0，Path Variable 缺口为 0。本轮没有新增、删除或重命名路径、operationId 或 Schema；此前 V3.4.0 报告中的 149 个 Schema 统计已按实际解析结果校正为 150。
- 两份 V18 SHA-256 均为 `3F2D3B19187E0801414E4C718553A32D467597FEEC1DBEA7F723C18475AC456D`，内容逐字一致。
- Testcontainers MySQL 8 从空库成功执行 V1-V18，最终版本 v18；业务表由 43 增至 44，`system_config` 保持 31，V1-V17 已应用 checksum 与原有 43 张表的列结构均保持不变。
- Flyway 定向测试 4/4 通过，实际验证 history 字段、两个外键、action_type CHECK、PLAN/TASK taskId CHECK、两个索引、schema-full 对齐及合法/非法行约束。
- `mvnw.cmd clean test`：通过，tests 162、failures 0、errors 0、skipped 0。
- `mvnw.cmd clean package`：通过，tests 162、failures 0、errors 0、skipped 0，可执行 JAR 打包成功。
- Mastery Algorithm V1.0 与 StudentResource 回归结果不变；未修改相关算法、契约或业务源码。

## V3.4.0 StudyPlan / StudyPlanTask 契约闭环

V3.4.0 是阶段八 B-2 实现前的新契约基线。本次修订收窄计划创建/更新职责，补齐任务持久化字段与乐观锁，并把数据库基线升级为 Flyway V17；Mastery Algorithm 仍为 V1.0。本次包含删除请求字段、重构 Update Schema 和状态迁移语义冻结，不属于兼容性 patch。

- `StudyPlanCreateRequest` 可携带初始任务，但不再接受 status；服务端固定创建 `DRAFT` 计划。`StudyPlanUpdateRequest` 已与 Create Schema 解耦，只更新 title、planType、startDate、endDate、dailyAvailableMinutes、description 和必填 version，不包含 studentId、status 或 tasks。
- `StudyPlanDto.tasks` 改为 `StudyPlanTaskDto[]`。任务响应完整暴露 id、studyPlanId、四类关联 ID、预期/实际时长、status、completedTime、version 和审计时间；所有 BIGINT ID 仍为 string。
- 任务创建固定 `TODO`，不接受 version、status、completedTime 或 actualDurationSeconds；普通任务 PUT 只允许 TODO、IN_PROGRESS、CANCELLED，完成和跳过使用专用操作。
- `CompleteStudyPlanTaskRequest` 和 `SkipStudyPlanTaskRequest` 均要求 version；完成时间由服务端生成，跳过任务不写 completedTime。四个计划状态操作统一要求 `StudyPlanStatusChangeRequest.version`。
- `listStudyPlanTasks` 新增统一 page/pageSize，并将 planId 明确放到 GET/POST operation 级。Java Spring 生成签名为 `String planId, LocalDate taskDate, StudyPlanTaskStatus status, StudyPlanTaskType taskType, Integer page, Integer pageSize`，路径变量生成缺口为 0。
- `generateStudyPlan` 保留 operationId，但明确延期到真实 AI 阶段；Stage 8B-2 不实现模板、随机或静默回退计划。

计划状态机冻结为 DRAFT→ACTIVE/CANCELLED，ACTIVE→PAUSED/COMPLETED/CANCELLED，PAUSED→ACTIVE/COMPLETED/CANCELLED；COMPLETED、CANCELLED、EXPIRED 为终态，EXPIRED 无人工操作。任务状态机冻结为 TODO→IN_PROGRESS/COMPLETED/SKIPPED/CANCELLED，IN_PROGRESS→TODO/COMPLETED/SKIPPED/CANCELLED；COMPLETED、SKIPPED、CANCELLED 为终态。计划完成不要求全部任务终态，也不改写任务状态；计划逻辑删除后通过父计划 `deleted=0` 隔离任务。

## Flyway V17 验证

V17 只为 `study_plan_task` 增加 `exam_id BIGINT NULL`、`actual_duration_seconds INT NULL`、`version INT NOT NULL DEFAULT 1`，以及 exam 外键、非负实际时长 CHECK 和六值 task_type CHECK。没有新增表、索引、配置或 academic_term_id。

- 两份 V17 SHA-256 均为 `E65973BBC66D1A8EE575FB66D8BC7AC2159E26C714D96546F23DDE5778D9A68F`，逐字一致。
- Testcontainers 定向回归共 4 项通过；V16 合法存量任务的 id 和原业务字段保持不变，升级后 version=1，actual_duration_seconds/exam_id 均为 NULL。
- V1-V16 已应用 checksum 保持不变；最终业务表 43、`system_config` 31，`schema-full.sql` 与迁移后 `study_plan_task` 列结构一致。

## V3.4.0 校验与生成

- Swagger CLI：通过。
- Redocly：errors 0、warnings 0。
- OpenAPI Generator 7.10.0 JAR validate：通过；仅报告 4 个既有未使用 Schema 建议。
- Node 24.18.0 下 `npx @openapitools/openapi-generator-cli` wrapper 在转发命令前失败；按既有治理方案改用 `JAVA_HOME` 指向的 Java 21 和缓存的 Generator 7.10.0 JAR。
- Java client、TypeScript Fetch、Maven Spring 生成：通过。Java 的 examId 为 String，version/actualDurationSeconds 为 Integer；TypeScript 分别为 string 和 number。
- 当前统计：86 个路径模板、116 个操作、149 个 Schema；operationId 为 116/116/0，生成 Java 的路径变量缺口为 0。
- `mvnw.cmd clean test`：通过，tests 162、failures 0、errors 0、skipped 0。
- `mvnw.cmd clean package`：通过，tests 162、failures 0、errors 0、skipped 0，可执行 JAR 打包成功。
- Testcontainers MySQL 8 从空库成功迁移到 v17；业务表 43、`system_config` 31。阶段六 Mastery 回归共 46 项（20 项重放引擎单元测试 + 26 项集成测试），预期完全不变。

后续性能候选仍包括 `study_plan(student_id,status,deleted,start_date,end_date)`、`study_plan_task(study_plan_id,task_date,status)` 和 `resource_history(student_id,resource_id,create_time,id)`，本次未新增索引。

## V3.3.0 学生资源队列契约

V3.3.0 新增独立的学生资源分配模型和 4 个公开操作：`listStudentResources`、`createStudentResource`、`getStudentResource`、`updateStudentResource`。创建、更新、详情和分页响应均使用具名 Schema，未新增物理删除接口。

- `StudentResourceStatus` 与全局 `ResourceStatus` 分别生成独立 Java/TypeScript 类型，编码均为 `WAITING`、`LEARNING`、`COMPLETED`、`REVIEW`、`ARCHIVED`，业务语义不复用。
- `StudentResourceCreateRequest` 的 `studentId`、`resourceId` 为必填 string，状态可选且默认 `WAITING`；重复 student + resource 返回 `409 DUPLICATE_DATA`。
- `StudentResourceUpdateRequest` 仅包含状态、备注和必填 version；旧 version 返回 `409 DATA_VERSION_CONFLICT`，不得修改 student/resource 关联。
- `StudentResourceDto` 同时返回 `resourceStatus` 和 `studentStatus`。全部 BIGINT ID 生成为 Java `String` / TypeScript `string`，时间生成为 Java `OffsetDateTime` / TypeScript `Date`。
- `ResourceSummaryDto.status` 继续引用 `ResourceStatus`，新增必填 `studentStatus`、`assignmentId`、`assignedTime`；最新进度无历史时保持 `null`。
- 86 个路径模板、116 个操作完成 path parameter 审计，缺失路径变量为 0；operationId 为 116/116/0。

## Dashboard 语义冻结

Dashboard 资源数量和待学列表只从 `student_resource_assignment` 读取，并过滤已删除或全局 `ARCHIVED` 资源；`waitingResources` 按 `assigned_time DESC, id DESC` 最多返回 5 条，不从 ResourceHistory、全局状态或 StudyPlan 推导。

任务统计只计算目标日期当前学生 `ACTIVE` 计划中的任务行，排除 `CANCELLED`，完成数使用 `COMPLETED`；due 使用 `next_review_time < nextDayStart` 并包含 overdue，overdue 使用 `next_review_time < dayStart`。指定学期时统计期为学期开始日至 `min(term.endDate, targetDate)`，目标日期早于学期开始日返回 422；未指定时为含目标日期的固定 30 天窗口。

固定列表上限为 scoreTrends 10、weakKnowledge 5、dueReviews 5、waitingResources 5、recentStudyLogs 5。排序和数据来源已写入 `DashboardDto` 字段说明；AI 模块未实现时 `aiSuggestions=[]`。

## Flyway V16 验证

V16 只创建 `student_resource_assignment`，字段为 id、student_id、resource_id、status、assigned_time、remark、version、create_time、update_time；包含 student/resource 外键、`UNIQUE(student_id, resource_id)`、五状态 CHECK、`INT NOT NULL DEFAULT 0` 乐观锁和学生状态查询索引，不使用 deleted。

- 两份 V16 SHA-256 均为 `1436BB7F9EBCC358DDA0964829F2B402A34B29810E9040BF2918327F110BA9C9`，逐字一致。
- Testcontainers MySQL 8 从空库执行 V1-V16 成功，最终版本 v16，业务表 43，`system_config` 31。
- V1-V15 已应用 checksum 在执行 V16 前后完全一致；原 42 张业务表的列结构签名完全一致，V16 只增加一张表。
- `schema-full.sql` 的 assignment 列签名与迁移后实表一致。
- `FlywayMigrationIntegrationTest` 共 3 项均通过，其中新增独立 V15→V16 结构回归场景。

## V3.3.0 校验、生成与项目回归

- `npx swagger-cli validate api/openapi.yaml`：通过，exit 0。
- `npx @redocly/cli lint api/openapi.yaml`：通过，errors 0、warnings 0。
- `npx @openapitools/openapi-generator-cli validate -i api/openapi.yaml`：Node 24.18.0 wrapper 在命令转发前退出 1；同版本 OpenAPI Generator CLI 7.10.0 JAR validate 通过，exit 0，仅报告 4 个既有未使用 Schema 建议。
- Java client generation、TypeScript Fetch generation、Maven Spring generation：均通过，exit 0。
- `mvnw.cmd clean test`：通过，tests 143、failures 0、errors 0、skipped 0。
- `mvnw.cmd clean package`：通过，tests 143、failures 0、errors 0、skipped 0，JAR 打包成功。
- Mastery Algorithm V1.0 的阶段六 26 项测试结果未变化；未修改算法文档或实现。

## V3.2.1 ResourceHistory 路径参数修订

`/resources/{resourceId}/history` 原先只在 Path Item 级声明共享 `resourceId`。OpenAPI Generator 7.10.0 将该参数生成进 `createResourceHistory`，却没有生成进同一路径的 `listResourceHistory`，导致 GET 的 Java 方法不能显式定位资源。

V3.2.1 将共享参数移动到 POST 和 GET 各自的 operation 级，每个 operation 仅保留一次有效定义。URL、HTTP method、operationId、请求体、响应及业务语义均未改变。

- `createResourceHistory` 修改前：`createResourceHistory(String resourceId, ResourceHistoryCreateRequest request)`。
- `createResourceHistory` 修改后：`createResourceHistory(String resourceId, ResourceHistoryCreateRequest request)`；签名保持稳定，但参数来源改为 operation 级显式声明。
- `listResourceHistory` 修改前：`listResourceHistory(String studentId, Integer page, Integer pageSize)`。
- `listResourceHistory` 修改后：`listResourceHistory(String resourceId, String studentId, Integer page, Integer pageSize)`。
- TypeScript Fetch 的 `CreateResourceHistoryRequest` 和 `ListResourceHistoryRequest` 均显式包含必填 `resourceId: string`。
- 全量审计 71 个带路径模板变量的 operation，生成 Java 方法均包含对应 `@PathVariable`；`listMasteryHistory`、`unlockKnowledgeMastery`、Resource 和 StudyLog 未发现其他同类缺口。

## V3.2.1 校验与回归

- `npx swagger-cli validate api/openapi.yaml`：通过，exit 0。
- `npx @redocly/cli lint api/openapi.yaml`：通过，errors 0、warnings 0。
- `npx @openapitools/openapi-generator-cli validate -i api/openapi.yaml`：Node 24.18.0 wrapper 在进入校验前启动失败；OpenAPI Generator CLI 7.10.0 Maven 执行入口 validate 通过，exit 0。
- Java client generation、Spring Maven generation 和 TypeScript Fetch generation：均通过，exit 0。
- `mvnw.cmd clean test`：通过，tests 112、failures 0、errors 0、skipped 0。
- `mvnw.cmd clean package`：通过，tests 112、failures 0、errors 0、skipped 0，打包成功。
- Testcontainers MySQL 8 从空数据库成功执行 Flyway V1-V15，最终版本为 v15；业务表仍为 42 张，`system_config` 仍为 31 项，Mastery Algorithm V1.0 测试结果无变化。

## V3.2.0 Resource 契约纠正

V3.2.0 纠正学习资源难度字段与既有数据库设计不一致的问题：

1. `ResourceCreateRequest`、`ResourceUpdateRequest` 和 `ResourceDto` 共享的 `difficulty` Schema 从可空 `number` 收窄为可空 `integer`，范围仍为 0–5，字段继续保持可选。
2. `learning_resource.difficulty` 从数据库基线开始即为可空 `TINYINT`，无默认值且没有额外范围 CHECK；本次没有数据库迁移，Flyway 基线仍为 V15。
3. `coverAttachmentId` 保留原名称、类型和可选状态。当前版本不支持设置非空值，服务端应返回业务校验错误；字段仅为后续附件关系兼容扩展保留。
4. 其他模块的同名 `difficulty`、考试分数、掌握度和置信度等数值字段均未修改。

`number` 到 `integer` 属于 Schema 收窄，因此本次不是向后兼容 patch。阶段七 Resource 业务尚未实现，当前没有已发布后端依赖小数难度语义，且数据库设计始终使用 `TINYINT`，故在实现前将契约升级为 V3.2.0 以恢复一致性。

## V3.2.0 校验与生成

- `npx swagger-cli validate api/openapi.yaml`：通过，exit 0。
- `npx @redocly/cli lint api/openapi.yaml`：通过，errors 0、warnings 0。
- `npx @openapitools/openapi-generator-cli validate -i api/openapi.yaml`：Node 24.18.0 下 wrapper 在进入校验前启动失败；改用 OpenAPI Generator CLI 7.10.0 的 Maven 执行入口后 validate 通过，exit 0。
- Java 模型生成：通过，exit 0；别名对应的 `ResourceCreate`、`ResourceUpdate`、`Resource` 生成模型中，`difficulty` 均生成为 `Integer`。
- TypeScript Fetch 生成：通过，exit 0；上述生成模型的 `difficulty` 生成为 `number`，整数范围由 OpenAPI Schema 约束。
- 生成抽查确认考试分数、掌握度等真正需要小数的 Java 字段仍为 `BigDecimal`。
- `mvnw.cmd clean test`：通过，tests 112、failures 0、errors 0、skipped 0。
- `mvnw.cmd clean package`：通过，tests 112、failures 0、errors 0、skipped 0，打包成功。
- Testcontainers MySQL 8 从空数据库成功执行 Flyway V1-V15，最终版本为 v15；业务表仍为 42 张，`system_config` 仍为 31 项，Mastery Algorithm V1.0 测试预期无变化。

试生成目录位于 `target`，不提交到 Git。

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

本轮审查完成，OpenAPI V3.3.0 与 Flyway V16 可作为阶段八实现前的新契约和数据库基线。校验器、OpenAPI Generator validate、Java/Spring 试生成、TypeScript Fetch 试生成、全量测试和打包均通过。生成目录仅写入 `target`，不提交到仓库。

## 结构统计

| 项目 | 结果 |
|---|---:|
| OpenAPI 规范版本 | 3.1.0 |
| 契约基线版本 | 3.3.0 |
| 文件总行数 | 709 |
| 路径模板数量 | 86 |
| operation 数量 | 116 |
| operationId 总数 | 116 |
| operationId 唯一数 | 116 |
| 重复 operationId | 0 |
| Schema 数量 | 150 |
| Response 组件数量 | 69 |
| Parameter 组件数量 | 21 |
| 根节点重复 | 0 |

根节点 `openapi`、`info`、`servers`、`security`、`paths`、`components` 均各出现一次。所有路径变量均由 `required: true` 的 path parameter 声明；`assignmentId` 在详情和更新操作中均生成显式 Java `String` path 参数。

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
- `ResourceStatus` 与 `StudentResourceStatus` 是两个独立 Schema；assignment 同时关联全局资源和学生状态，API 不再以单一 status 混用两个维度。
- Flyway V16 将业务表数量增加到 43，未改变原有表字段、V1-V15 checksum、Mastery Algorithm V1.0 或 `system_config=31`。

## 尚未解决的问题

无契约或迁移阻塞问题。StudentResource、StudyPlan 和 Dashboard 业务实现不属于本轮，尚未开始；AI 模块完成前 Dashboard 的 `aiSuggestions` 固定为空数组。`coverAttachmentId` 的附件关系编码及唯一性规则尚未冻结，当前版本只保留字段并拒绝非空值。OpenAPI Generator 对 OpenAPI 3.1 的 beta 提示及既有未使用 Schema 建议不影响 validate、生成或 Maven 编译结果。Node 24 下 npx wrapper 仍无法稳定启动，已通过同版本 Generator CLI 7.10.0 JAR 完成 validate 和试生成。真实 JWT 鉴权仍属于后续阶段。
