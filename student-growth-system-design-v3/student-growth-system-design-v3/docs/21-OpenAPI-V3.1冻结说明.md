# OpenAPI V3.4.1 冻结说明

## V3.4.1 StudyPlan 操作历史与 Flyway V18

V3.4.1 是阶段八 B-2 StudyPlan/StudyPlanTask 开发的新契约基线。计划与任务状态机继续沿用 V3.4.0，既有请求字段结构、类型、必填性、路径和 operationId 均不改变；本次只补齐命令字段的持久化语义。数据库基线升级为 Flyway V18，Mastery Algorithm 保持 V1.0。

1. `StudyPlanStatusChangeRequest.reason` 持久化到不可变 `study_plan_action_history.reason`，适用于 `PLAN_ACTIVATE`、`PLAN_PAUSE`、`PLAN_COMPLETE`、`PLAN_CANCEL`，不得覆盖计划 `description`。
2. `SkipStudyPlanTaskRequest.reason` 持久化为 `TASK_SKIP` 历史的 `reason`，不得覆盖任务 `remark`。
3. `CompleteStudyPlanTaskRequest.note` 持久化为 `TASK_COMPLETE` 历史的 `note`，不得覆盖任务 `remark`。
4. 状态更新和历史写入必须处于同一事务。历史写入失败时状态更新回滚；stale version、非法状态机和其他失败命令不得写历史。
5. 历史记录保存真实 `from_status`、`to_status`、`version_before` 和 `version_after`；后续 Service 必须保证 `version_after = version_before + 1`。
6. `PLAN_*` 历史不关联任务，`TASK_*` 历史必须关联任务。V18 通过 CHECK 在数据库层固定该规则，并保留 plan/task 两个维度的时间顺序索引。
7. 普通计划/任务 create、update、delete 不写 action history；当前不新增公开历史查询 API。
8. `generateStudyPlan` 继续延期到真实 AI 阶段，不得提供模板、随机或静默回退实现。

V18 只新增 `study_plan_action_history`，不修改原有 43 张业务表和 `system_config`，不使用逻辑删除或乐观锁字段，也不允许业务 UPDATE/DELETE。V1-V17 继续冻结。

V3.4.1 与 V18 已通过 Swagger、Redocly、OpenAPI Generator validate、Java/TypeScript 试生成和 Maven Spring 生成。Testcontainers MySQL 8 从空库迁移至 v18，最终为 44 张业务表、31 项 `system_config`；全量 `clean test` 和 `clean package` 均为 162 项测试通过。两份 V18 内容及 SHA-256 一致，`schema-full.sql` 与迁移后 history 表列结构一致。

## V3.4.0 StudyPlan / StudyPlanTask 契约与 Flyway V17

V3.4.0 是阶段八 B-2 StudyPlan/StudyPlanTask 开发的新契约基线，数据库基线同步升级为 Flyway V17，Mastery Algorithm 保持 V1.0。本次包含请求 Schema 收窄和职责重构，不作为兼容性 patch；阶段八 B-2 业务尚未实现，因此在实现前恢复契约、数据库与 Dashboard 已冻结口径的一致性。

### Plan 职责与状态

1. 创建计划可携带初始任务，但客户端不能提交 status；服务端固定创建 DRAFT。
2. 更新计划只允许 title、planType、startDate、endDate、dailyAvailableMinutes、description 和必填 version，不允许修改 studentId、status 或 tasks。
3. 计划状态只能由 activate、pause、complete、cancel 独立操作改变，全部使用 version 乐观锁。DRAFT 可到 ACTIVE/CANCELLED；ACTIVE 可到 PAUSED/COMPLETED/CANCELLED；PAUSED 可到 ACTIVE/COMPLETED/CANCELLED；COMPLETED、CANCELLED、EXPIRED 为终态，EXPIRED 暂无人工操作。
4. 完成计划不要求全部任务为终态，也不自动修改任务状态。删除计划只逻辑删除父记录，不物理删除或取消任务；所有正常计划/任务查询通过父计划 `deleted=0` 隔离。
5. planType 继续使用普通字符串，不新增枚举；计划仍以 student + startDate + endDate 表达，不增加 academic_term_id。

### Task 职责与关联

1. 创建任务要求 taskDate、taskType、title，服务端固定状态 TODO；不得提交 id、version、status、completedTime 或 actualDurationSeconds。
2. 普通 PUT 要求 version，可编辑任务元数据和关联 ID；status 只接受 TODO、IN_PROGRESS、CANCELLED。COMPLETED、SKIPPED 必须使用专用操作，终态任务不得重新打开。
3. TODO 可转 IN_PROGRESS、COMPLETED、SKIPPED、CANCELLED；IN_PROGRESS 可转 TODO、COMPLETED、SKIPPED、CANCELLED；COMPLETED、SKIPPED、CANCELLED 为终态。
4. 完成请求要求 version，可提交非负 actualDurationSeconds；completedTime 由服务端写入当前时间。跳过请求要求 reason 和 version，completedTime 保持 NULL。
5. WRONG_QUESTION_REVIEW 仅允许且要求 wrongQuestionId；RESOURCE_LEARNING 仅允许且要求 resourceId；KNOWLEDGE_PRACTICE 仅允许且要求 knowledgeId；EXAM_REVIEW 仅允许且要求 examId；READING/OTHER 的四个关联 ID 必须全部为空。
6. 后端实现必须校验错题和考试属于计划学生、资源存在且未删除、知识点存在且启用。taskDate 必须满足 `plan.startDate <= taskDate <= plan.endDate`；更新计划日期若排除已有任务，返回 422 `BUSINESS_RULE_VIOLATION`，不得自动移动、删除或截断任务日期。
7. `StudyPlanDto.tasks` 固定为 `StudyPlanTaskDto[]`；`listStudyPlanTasks` 显式接收 planId、page、pageSize。全部数据库 BIGINT ID 继续通过 API 返回 string。

### 数据库与延期边界

Flyway V17 仅为 `study_plan_task` 增加 `exam_id BIGINT NULL`、`actual_duration_seconds INT NULL`、`version INT NOT NULL DEFAULT 1`，并增加 exam 外键、非负实际时长 CHECK 和六值 task_type CHECK。V1-V16 继续冻结；业务表保持 43，`system_config` 保持 31。本次没有新增性能索引。

`generateStudyPlan` 仅保留契约，延期到真实 AI 阶段；阶段八 B-2 不得提供模板、随机计划或无模型静默回退。Dashboard V3.3.0 的任务口径保持不变：只统计当前学生 ACTIVE 且未删除父计划中目标日期的任务，排除 CANCELLED。

性能候选 `study_plan(student_id,status,deleted,start_date,end_date)`、`study_plan_task(study_plan_id,task_date,status)`、`resource_history(student_id,resource_id,create_time,id)` 必须等待 EXPLAIN 或真实数据量证据后再通过后续 Flyway 评估。

## V3.3.0 学生资源队列与 Dashboard 语义冻结

V3.3.0 是阶段八实现前的新契约基线。本次引入独立的学生资源分配模型，并将数据库基线升级到 Flyway V16；Mastery Algorithm 仍为 V1.0。

### 资源与学生资源

1. `learning_resource` 继续表示全局学习资源，`ResourceStatus` 只表达资源本身状态。
2. `student_resource_assignment` 表示某学生被分配的资源及学习状态，`StudentResourceStatus` 是独立业务枚举，编码为 `WAITING`、`LEARNING`、`COMPLETED`、`REVIEW`、`ARCHIVED`。
3. 两个状态枚举即使编码相同也不得在 Java 层复用为同一类型。一个学生与一个资源最多存在一条 assignment；归档通过 `StudentResourceStatus.ARCHIVED` 表达，不使用逻辑删除或物理删除接口。
4. 新增 `/student-resources` 列表、创建、详情和更新接口。创建要求 student 存在且 resource 存在并未删除；重复创建返回 `409 DUPLICATE_DATA`。更新只能修改状态和备注，旧 version 返回 `409 DATA_VERSION_CONFLICT`。
5. `ResourceSummaryDto.status` 继续使用全局 `ResourceStatus`，并新增 `studentStatus`、`assignmentId`、`assignedTime`。`latestProgressPercent` 取当前 student + resource 按 `create_time DESC, id DESC` 排序的最新 `ResourceHistory.progressPercent`，不存在历史时返回 `null`。

### ResourceHistory 联动

创建 ResourceHistory 时，仅在对应 assignment 已存在时执行状态联动：`completed=true` 且 assignment 非 `ARCHIVED` 时更新为 `COMPLETED`；否则当 `progressPercent>0` 且当前为 `WAITING` 时更新为 `LEARNING`。`REVIEW` 和 `ARCHIVED` 不被普通历史自动覆盖。不存在 assignment 时不得自动创建，ResourceHistory 仍可独立存在。

### Dashboard 冻结口径

1. `waitingResourceCount` 统计当前学生 `studentStatus=WAITING`，且关联资源 `deleted=false`、全局状态非 `ARCHIVED` 的 assignment；`learningResourceCount` 使用相同有效资源条件并统计 `studentStatus=LEARNING`。
2. `waitingResources` 只取有效的 `WAITING` assignment，按 `assigned_time DESC, id DESC` 排序，最多 5 条；不得从 ResourceHistory 缺失、全局资源状态或 `study_plan_task` 推导。
3. `totalTaskCount` 统计目标日期、当前学生 `ACTIVE` 计划下状态非 `CANCELLED` 的任务行；`completedTaskCount` 统计其中 `COMPLETED` 的任务行。重复 resourceId 仍按任务行计数，非 `ACTIVE` 计划不计入。
4. Dashboard ZoneId 下，`overdueReviewCount` 使用 `next_review_time < dayStart`，`dueReviewCount` 使用 `next_review_time < nextDayStart`，因此 due 包含 overdue；记录必须未删除且非 `ARCHIVED`。
5. 指定 academicTermId 时，统计期为 `term.startDate` 至 `min(term.endDate, targetDate)`；targetDate 早于学期开始日返回 422，禁止产生 startDate 晚于 endDate。未指定学期时固定使用 `targetDate-29 days` 至 `targetDate` 的 30 天窗口。
6. 固定列表上限为：`scoreTrends=10`、`weakKnowledge=5`、`dueReviews=5`、`waitingResources=5`、`recentStudyLogs=5`，本版本不增加 system_config。
7. `scoreTrends` 取统计期内最近 10 个有效点后按 examDate 升序返回；`weakKnowledge` 按 `mastery_score ASC, evidence_count DESC, update_time DESC, id ASC` 取真实 student_mastery，最多 5 条且不创建无证据记录。
8. `dueReviews` 使用 Dashboard due 定义，按 `next_review_time ASC, id ASC` 最多 5 条；`recentStudyLogs` 限统计期内当前学生未删除日志，按 `study_date DESC, create_time DESC, id DESC` 最多 5 条。
9. AI 模块未实现时，`aiSuggestions` 固定返回空数组，不生成模板建议。

### 数据库基线

Flyway V16 只创建 `student_resource_assignment`，包含 student/resource 外键、`UNIQUE(student_id, resource_id)`、独立状态 CHECK 和 `INT NOT NULL DEFAULT 0` 乐观锁字段；不修改 V1-V15 的表结构、配置或迁移内容。数据库业务表由 42 张增加到 43 张，`system_config` 保持 31 项。

## V3.2.1 ResourceHistory 路径参数修订

V3.2.1 是阶段七正式实现的新契约基线。本次只修复 `/resources/{resourceId}/history` 的生成参数完整性：

1. `createResourceHistory` 在 operation 级显式声明必填 `resourceId`。
2. `listResourceHistory` 在 operation 级显式声明必填 `resourceId`，并继续保留 `studentId`、`page`、`pageSize`。
3. 原 Path Item 级共享参数已移除，POST 和 GET 每个 operation 中均只有一个有效 `resourceId` 定义。
4. URL、HTTP method、operationId、请求/响应结构和业务语义均未变化。
5. 数据库没有变化，Flyway 基线仍为 V15，Mastery Algorithm 仍为 V1.0。

生成后的 Java 和 TypeScript Fetch 方法都显式接收 `resourceId`，Controller 不需要也不得通过 `HttpServletRequest`、URI template variables 或手工 URI 解析获取资源 ID。

## V3.2.0 Resource 契约纠正

V3.2.0 是阶段七 Resource 业务实现前的新契约基线。本次纠正冻结契约与既有数据库设计之间的类型冲突：

1. Resource `difficulty` 正式定义为可选、可空的整数等级，合法范围为 0–5，不接受小数值。
2. 数据库 `learning_resource.difficulty` 继续使用可空 `TINYINT`；本次不修改数据库、不新增迁移，Flyway 基线仍为 V15。
3. `coverAttachmentId` 仅保留契约占位能力。当前后端版本不支持设置非空值，收到非空值时应返回业务校验错误；附件关系编码和唯一性规则留待附件模块冻结后扩展。
4. `learning_resource` 是全局资源，不承载特定学生的学习状态。
5. `resource_history` 表示 `student + resource` 的学习事件，不自动修改全局 `ResourceStatus`。
6. `ResourceDto` 不承载特定学生的观看进度，学生维度进度通过资源历史接口表达。

`difficulty` 从 `number` 收窄为 `integer`，属于 Schema 收窄，不是向后兼容字段扩展，也不标记为 V3.1.x compatible patch。允许此次升级是因为阶段七业务尚未实现、没有已发布 Resource 后端依赖小数语义，且数据库从设计开始即使用 `TINYINT`。其他模块 API、Mastery Algorithm V1.0 和数据库结构均未变化。

## Flyway V15 数据库基线修订

OpenAPI 契约版本保持 V3.1.3，Mastery Algorithm 保持 V1.0。数据库基线升级至 Flyway V15，V15 仅为 `study_log` 增加 `INT NOT NULL DEFAULT 0` 的 `version` 字段，用于满足既有 `StudyLogUpdateRequest.version` 和 `StudyLogDto.version` 乐观锁契约。

本次没有 API 兼容性变化，没有修改其他表、索引或算法配置。Flyway V1-V14 继续冻结，后续数据库变更必须使用新的迁移版本。

## V3.1.3 Mastery 兼容修订

V3.1.3 是阶段七开始前的新契约基线。本次只修复 Mastery 输出完整性和生成接口参数完整性：

1. `MasteryDto` 新增可选只读字段 `evidenceCount` 和 `nextReviewTime`，用于暴露已经持久化的证据数量和下次复习时间。
2. `listMasteryHistory` 在不改变 URL 和 operationId 的前提下，将 `knowledgeId` 明确声明为操作级必填 path 参数。
3. Java Spring 生成接口显式接收 `String knowledgeId`；Controller 不再从 `HttpServletRequest` 的 URI template variables 手工提取业务 path 参数。
4. 本次没有删除接口或字段，没有修改已有字段类型、必填属性或枚举，因此属于向后兼容修订。

V3.1.3 契约修订完成时 Mastery Algorithm 为 V1.0、Flyway 为 V14；当前数据库冻结基线已由上述 V15 修订更新，契约和算法版本均未变化。

## V3.1.2 掌握度契约修订

V3.1.2 是阶段六掌握度模块的新契约基线。本次只修复掌握度记录唯一定位缺口：

1. `unlockKnowledgeMastery` 保留原 path、`knowledgeId` 语义和 operationId，新增必填 query 参数 `studentId`。
2. 当前没有真实 JWT 学生上下文，解锁必须显式使用 `studentId + knowledgeId` 定位 `student_mastery`。
3. `adjustKnowledgeMastery` 的既有请求已经包含 `studentId`，不增加重复参数。
4. 人工调整设置目标分数并锁定结果；解锁后立即按掌握度算法 V1.0 重放有效证据。无有效证据时返回 `409 BUSINESS_RULE_VIOLATION`。
5. 没有删除接口、字段或 operationId，没有修改已有字段类型，也没有修改其他业务模块 API。

Flyway V14 只向现有 `system_config` 增加掌握度算法版本、成绩分类阈值和时间衰减开关，不执行 `ALTER TABLE`，不改变 42 张业务表的结构。完整算法见 `docs/22-掌握度算法V1.0.md`。

## V3.1.1 兼容性修订

V3.1.1 是阶段四考试与成绩模块的新契约基线，在 V3.1 的基础上仅新增可选字段和响应 Schema：

1. `SubjectScore` 新增 `classSize`、`gradeSize` 与 `knowledgeScores` 输入。
2. 新增 `ScoreKnowledgeInput`、`ScoreKnowledgeDto` 与 `SubjectScoreDto`；考试响应使用 `SubjectScoreDto` 返回知识点编码、名称和计算比率。
3. `Exam` 新增只读 `totalScore`、`totalFullScore`、`totalScoreRate`。客户端不能提交总分字段，服务端必须根据单科成绩汇总。
4. 所有新增数据库 BIGINT 标识继续通过 API 以 `string` 返回。

这是一项向后兼容变更：没有删除路径、字段或 operationId，也没有修改既有字段类型和数据库/Flyway 基线。排名与知识点的跨字段约束由服务端校验：排名存在时必须给出对应人数且不得超过人数；知识点必须属于当前学科，且分数和正确题数不得超过各自总量。

## 冻结结论

OpenAPI V3.4.1 是阶段八 B-2 实现前的契约基线。后续开发必须按 `api/openapi.yaml` 和掌握度算法 V1.0 实现，不得自行猜测字段、状态、响应结构、持久化映射或计算公式。

本冻结包括统一响应模型、分页模型、错误模型、安全定义、文件上传下载约束、异步任务模型以及核心枚举。所有数据库 BIGINT ID 通过 API 返回 `string`。核心枚举编码与数据库 V1 至 V13 的 CHECK 约束保持一致。

## 开发规则

1. 新增接口必须先修改并校验 OpenAPI，再实现后端。
2. 已发布接口禁止随意删除或修改语义。
3. 新增可选字段通常属于兼容变更。
4. 新增接口通常属于兼容变更。
5. 删除字段属于不兼容变更。
6. 修改字段类型属于不兼容变更。
7. 修改枚举编码属于不兼容变更。
8. 修改必填属性必须经过兼容性审查。
9. 分页、错误响应、文件 MIME、任务状态和幂等约束必须继续复用冻结组件。

## 数据库边界

数据库 V1 至 V17 已冻结，不得回写或重定义既有迁移的业务语义。V18 只创建不可变 `study_plan_action_history`，不修改原有业务表字段或 system_config；后续数据库变更必须使用新的迁移版本。

## 安全边界

契约已固定 `bearerAuth` 和 JWT Bearer 方案，但当前阶段不代表真实 JWT 鉴权已经实现。鉴权代码、Controller、Service、Entity、前端和真实 AI/文件/恢复执行均不属于本冻结说明的实现内容。

## 验证记录

本轮 `swagger-cli`、Redocly 和 OpenAPI Generator validate 均通过；Java 模型与 TypeScript Fetch 试生成通过。详细统计和一致性结论见 `api/openapi-validation-report.md`。
