# OpenAPI V3.1.3 冻结说明

## V3.1.3 Mastery 兼容修订

V3.1.3 是阶段七开始前的新契约基线。本次只修复 Mastery 输出完整性和生成接口参数完整性：

1. `MasteryDto` 新增可选只读字段 `evidenceCount` 和 `nextReviewTime`，用于暴露已经持久化的证据数量和下次复习时间。
2. `listMasteryHistory` 在不改变 URL 和 operationId 的前提下，将 `knowledgeId` 明确声明为操作级必填 path 参数。
3. Java Spring 生成接口显式接收 `String knowledgeId`；Controller 不再从 `HttpServletRequest` 的 URI template variables 手工提取业务 path 参数。
4. 本次没有删除接口或字段，没有修改已有字段类型、必填属性或枚举，因此属于向后兼容修订。

Mastery Algorithm 仍为 V1.0，Flyway 仍为 V14，数据库和算法实现均无变化。

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

OpenAPI V3.1.3 是阶段七开始前的契约基线。后续开发必须按 `api/openapi.yaml` 和掌握度算法 V1.0 实现，不得自行猜测字段、状态、响应结构或计算公式。

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

数据库 V1 至 V13 已冻结，不得回写或重定义既有迁移的业务语义。V14 只补充算法配置数据，不修改数据库结构；后续变更必须使用新的迁移版本。

## 安全边界

契约已固定 `bearerAuth` 和 JWT Bearer 方案，但当前阶段不代表真实 JWT 鉴权已经实现。鉴权代码、Controller、Service、Entity、前端和真实 AI/文件/恢复执行均不属于本冻结说明的实现内容。

## 验证记录

本轮 `swagger-cli`、Redocly 和 OpenAPI Generator validate 均通过；Java 模型与 TypeScript Fetch 试生成通过。详细统计和一致性结论见 `api/openapi-validation-report.md`。
