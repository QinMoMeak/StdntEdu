# OpenAPI V3.7.0 冻结说明

## V3.7.0 AI Extraction 资源安全边界

V3.7.0 是阶段十B开始前的新契约基线。本次只收窄 AI Extraction 上传、视觉解码和 Provider
归一化输入的资源边界，不新增路径、operationId、Schema、字段或数据库结构。由于部分此前可接受
的输入现在会被拒绝，本次是 minor 版本升级，不作为 V3.6.x 兼容性 patch。

### 六层独立限制

1. 单任务上传文件数最多 20。
2. 图片单文件最大 15 MB，PDF 单文件最大 50 MB。
3. 全部原始上传文件字节之和最大 268435456 bytes（256 MiB），不含 multipart boundary/header。
4. Normalized visual units 最多 20，仍按每张图片和每个 PDF 页面各 1 unit 计算。
5. 每个最终解码或渲染 visual 的宽、高分别不超过 8192，且总像素不超过 16000000。
6. 发送给 Provider 的全部 normalized image binary 在 Base64 前合计不超过 134217728 bytes
   （128 MiB）。Base64 和 JSON 膨胀不计入该业务值。

六层限制必须分别校验，通过后层不得替代前层。超出任一容量限制均返回 413
`PAYLOAD_TOO_LARGE`，不得通过降 DPI、降质量、丢页、改格式、拆批或切换 Provider 绕过。

### 图片与 PDF Preflight

1. JPEG、PNG 和 WEBP 必须在完整 decode 前使用 `ImageReader` 或等价安全 metadata API 读取
   width、height；宽高和 `width * height` 使用 `long` 校验。单 visual 的 raster working set
   固定按 `decodedPixels * 8 bytes` 预算且不得超过 128 MiB，不得依赖 `OutOfMemoryError` 校验输入。
2. PDF 必须先验证非加密、无需密码且至少 1 页。每页按 CropBox 和 page rotation 计算 150 DPI
   raster：每一边为 `ceil(effectivePoints / 72 * 150)`，90/270 度交换有效宽高；中间计算使用
   finite double 和 long，结果必须大于 0，再执行与图片相同的 8192/16000000 限制。
3. PDF 只允许本地安全解析和页面渲染，不执行 JavaScript、URI action、下载、外部资源访问或
   embedded executable；原始 PDF 永远不发送给 Provider。
4. Static lossy、lossless 和 alpha WebP 均支持，并在 Provider 调用前固定归一化为 PNG。
   Animated WebP（frame count > 1 或 animation flag=true）返回 422
   `BUSINESS_RULE_VIOLATION`；不得选择、合成或拆分帧，也不得创建业务记录。

### Provider Payload 与清理

Normalized binary 是实际发送给 Provider 的图片字节累计值：JPEG/PNG 使用实际发送 bytes，
WEBP 使用归一化 PNG bytes，PDF 使用 150 DPI、quality 0.92 的页面 JPEG bytes。超过 128 MiB
时立即停止后续归一化，清理 transient artifacts，返回 413 且不得发起 Provider 调用。Adapter
必须流式执行 Base64 和 HTTP body，禁止先构造完整 Base64 String 与完整 JSON String。

任务使用隔离的临时目录。成功、校验失败、Provider 失败、取消、超时和异常退出都必须清理
派生 PNG/JPEG 和其他瞬时文件；这些文件不创建 Attachment、不写数据库、不暴露路径。

### 错误语义与实现约束

- 413：文件数、单文件大小、原始文件总量、visual units、宽高/像素或 normalized binary 超限。
- 422：损坏或不可解析输入、加密/密码保护/零页 PDF、animated WebP 及其他业务校验失败。
- 415：真实 MIME/magic 不属于 JPEG、PNG、WEBP 或 PDF。

后续实现的 Spring multipart 基础设施配置应使用 `max-file-size=50MB`、
`max-request-size=270MB`、`file-size-threshold=0`。270 MB 只是覆盖 multipart overhead 的传输层
包络，256 MiB 原始文件总量仍由业务层按实际文件字节独立校验，并通过配置绑定测试固定单位。

依赖候选冻结为 PDFBox 3.0.8（Apache-2.0）和 TwelveMonkeys ImageIO WebP 3.14.0
（BSD-3-Clause）。如可靠拒绝公钥加密 PDF 确实需要 Bouncy Castle，只能在依赖与许可证审计后
按最小范围引入，不把它冻结为本次 API 依赖。

V3.7.0 没有数据库迁移。Flyway 仍为 V19，业务表仍为 47，`system_config` 仍为 31；本轮不创建
V20，不修改 `pom.xml`、application 配置、Java 源码或测试，也不开始 Stage10B 业务实现。

## V3.6.0 AI Extraction Visual Input

V3.6.0 是阶段十B AI 错题识别实现的新契约基线。本次只冻结 Provider 视觉输入和 PDF
归一化语义，不新增路径、字段、Schema、枚举或数据库结构。由于新增 normalized visual unit
上限并收窄客户端有效输入范围，本次是 minor 版本升级，不作为 V3.5.x 兼容性 patch。

### 输入与 Preflight

1. 客户端继续通过 multipart 上传 JPEG、PNG、WEBP 和 PDF。图片单文件最大 15 MB，PDF
   单文件最大 50 MB，上传文件数最多 20；文件大小、文件数与 visual unit 是彼此独立的限制。
2. `AiInputType` 只描述客户端原始输入组成：全部 JPEG/PNG/WEBP 为 `IMAGE`，全部 PDF 为
   `PDF`，两类并存为 `MIXED`。PDF 页面转换成 JPEG 后，任务 inputType 仍保持 PDF 或 MIXED。
3. Normalized visual unit 是内部处理语义，不新增公开 DTO：每张 JPEG/PNG/WEBP 计 1 unit，
   每个 PDF 页面计 1 unit，MIXED 为图片数加全部 PDF 页数；整个任务最多 20 units。超出返回
   413 `PAYLOAD_TOO_LARGE`。
4. 创建 Task 前必须检查真实 MIME/magic、可解析性、PDF 是否加密或需要密码、pageCount 是否
   至少为 1，以及任务 visual units 是否超限。损坏、不可解析、加密、密码保护或 0 页 PDF
   返回 422 `BUSINESS_RULE_VIOLATION`，不得形成业务 Attachment、Task 或临时题记录；临时文件
   必须清理。

### PDF 与图片归一化

1. 原始 PDF 永远不发送给 Provider。PDF 是应用内部输入格式，Provider 推理输入统一为
   `TEXT + IMAGE[]`，处理链固定为 PDF -> 本地页面渲染 -> raster image -> Provider Adapter。
2. PDF 每页按原顺序渲染为 RGB 图片，基准为 150 DPI、JPEG quality 0.92。标准 renderer
   可以应用页面自身 rotation，但不得 OCR、提取 text layer、重排页面、跳过空白页或改变页序。
3. PDF renderer 只能执行本地页面渲染，不得执行脚本、访问远程 URL 或加载外部网络资源。
4. 页面 JPEG 是 transient processing artifact：不创建 Attachment、不写数据库、不返回客户端，
   在任务处理结束后释放。原始 PDF Attachment 继续按正常附件生命周期保留。
5. JPEG、PNG 验证真实类型后可直接进入 Provider 视觉输入；WEBP 必须在 Provider 层之前解码并
   归一化为 PNG 或 JPEG，不能依赖具体 Provider 恰好支持 WEBP。Java 21 解码依赖及许可证留到
   Stage10B 实现阶段评审，本轮不修改 `pom.xml`。

### 顺序与 Provider Transport

1. Provider visual input 严格保持 multipart 文件顺序。PDF 在原文件位置展开为 page 1..N；
   不得按 MIME、文件名或其他属性重新排序。
2. 一个 Extraction Task 固定为一次 Provider extraction 请求，不实现 chunk、overlap、页面去重、
   分页多请求或 multi-call merge。未来若真实 payload 限制需要分块，必须另立契约版本。
3. `OPENAI_COMPATIBLE` 使用 `POST {baseUrl}/chat/completions`，不得隐藏追加 `/v1`。请求设置
   `model=modelName`、`stream=false`，user message 使用 TEXT part 和 IMAGE parts；JPEG/PNG
   分别通过 `data:image/jpeg;base64,...`、`data:image/png;base64,...` 表示。兼容范围仅包括
   multimodal chat completions 子集，不承诺 Files、Assistants、Responses API 或 PDF native input。
4. `OLLAMA` 使用 `POST {baseUrl}/api/chat`，请求设置 `model=modelName`、`stream=false`；user
   message 的 `content` 是 extraction prompt，`images` 是 normalized image bytes 的纯 base64
   数组，不含 data URL 前缀。PDF 页面到达 Adapter 前已转换为 JPEG，禁止把 PDF bytes 放入 images。
5. Ollama 可使用 `format=json` 或官方 JSON schema structured output；所有 Adapter 都必须返回
   内部强类型 `AiExtractionProviderResult`，不得把 Provider 原始 JSON直接传给 Service。JSON 不符合
   应用预期时任务失败，不保存半解析临时题。

### 失败与安全边界

Provider 因 payload、图片、多模态、context limit 或协议不兼容拒绝请求时，整个 Task 失败；
不得减少页面、降低 DPI、丢弃输入、OCR、分批、自动切换协议或静默回退。Preflight 成功后若页面
渲染失败，Task 同样进入 `FAILED`，释放已生成的临时图片且不保存部分问题。Prompt 可以按业务需要
包含原始显示文件名和页序号，但不得包含 `attachment.storage_path`、本地路径、Secret、API Key 或
无关学生数据。

V3.6.0 没有数据库变化。Flyway 仍为 V19，业务表仍为 47，`system_config` 仍为 31；V19 已能保存
原始 Attachment、Extraction Task、input_type、临时题和确认记录，页面图片无需持久化，因此禁止创建
空 V20 或向 Attachment 增加 page/converted/rendered path 字段。

## V3.5.0 AI 基础安全与 Flyway V19

V3.5.0 是阶段十 AI 后端开发的新契约基线，数据库基线同步升级为 Flyway V19，Mastery Algorithm 保持 V1.0。本次包含模型字段重构、并发契约、Secret 安全模型、识别确认幂等闭环及异步计划生成响应修订，属于 AI 子系统的大版本契约变更，不是兼容性 patch。V1-V18 保持冻结。

### AI Model 与 Provider

1. `AiModel` 必须明确提供 `provider`、`modelType`、`protocol`、`authType`、`baseUrl` 和 `modelName`。Provider 只表示品牌或来源，Protocol 决定请求格式，AuthType 决定是否需要 Secret；服务端不得根据 Provider 名称隐藏推断协议或默认 URL。
2. `AiModelType` 固定为 `CHAT`、`MULTIMODAL`、`EMBEDDING`；错题图片/PDF识别只允许 `MULTIMODAL`，计划生成允许 `CHAT` 或 `MULTIMODAL`，`EMBEDDING` 不得用于这两类任务。
3. `AiProtocol` 固定为 `OPENAI_COMPATIBLE`、`OLLAMA`；`AiAuthType` 固定为 `NONE`、`BEARER_API_KEY`。已禁用模型不得启动新任务；已开始任务在模型被停用后的行为留待任务实现阶段冻结。
4. `temperature` 是可空的 `0–2` 模型默认值，数据库使用 `DECIMAL(4,3)`；`maxTokens` 是可空正整数。未来单次请求覆盖值必须另行修订契约。
5. `AiModelUpdateRequest.version` 以及启用/停用使用的 `AiModelStatusChangeRequest.version` 均必填。状态操作成功后 version 加一，旧 version 返回 `409 DATA_VERSION_CONFLICT`。数据库 version 遵循非 StudyPlan 实体规范，使用 `INT NOT NULL DEFAULT 0`。
6. `testAiModelConnection` 不接收请求体，只使用已保存配置，并返回 `AiModelConnectionTestDto`。它是只读操作，不修改 enabled、version、API Key 或其他模型状态；Provider 错误、响应体和 URL 中的敏感信息必须脱敏。

### Secret 安全边界

1. Provider Secret 使用 AES-256-GCM 加密。主密钥只从环境变量 `STDNTEDU_AI_SECRET_MASTER_KEY` 读取，格式为 Base64 编码的 32-byte key；不得写入数据库、YAML、Git、日志或 API。
2. 每个 Secret 使用独立随机 12-byte nonce和标准 GCM 认证标签，AAD 为 `secret_ref` 的 UTF-8 字节；`algorithm` 固定为 `AES-256-GCM`，当前 `key_version=1`。V3.5.0 不实现自动密钥轮换。
3. `ai_model.api_key_ref` 通过外键引用 `ai_secret.secret_ref`。完整 `apiKey` 仅能通过模型创建/更新请求以 `writeOnly` 输入，不提供 Secret 查询 API。
4. 更新时：省略 `apiKey` 且 `clearApiKey=false` 保留原 Secret；提供非空 `apiKey` 且不清除时新建或替换；`clearApiKey=true` 时清除引用并删除 Secret。`apiKey` 与清除标志同时提交、或提交空字符串，返回 422。
5. `apiKeyConfigured` 仅由引用是否存在判断；`apiKeyMasked` 使用数据库中的非敏感 `mask_suffix` 生成 `****` 加后缀，不得为了显示掩码而解密 Secret。
6. `AiModelDto`、统一错误、`fieldErrors.rejectedValue`、operation log、应用日志和 Provider 异常均不得包含 `apiKey`、Authorization、token、secret 或 password。当前 `GlobalExceptionHandler` 可能回显 rejectedValue，AI 实现阶段必须统一脱敏，本轮不修改 Java 源码。

### Extraction 与幂等确认

1. `AiInputType` 固定为 `IMAGE`、`PDF`、`MIXED`，由服务端按实际 MIME 推导，客户端不得提交。全 JPEG/PNG/WEBP 为 IMAGE，全 PDF 为 PDF，两者混合为 MIXED。
2. 图片单文件最大 15 MB，PDF 单文件最大 50 MB，单任务最多 20 个文件；其他 MIME 返回 415，超限返回 413。业务实现阶段必须显式配置 Spring multipart 限制，不得依赖框架默认值，本轮不修改 application YAML。
3. 临时题响应与更新请求均使用必填 version；旧版本更新返回 `409 DATA_VERSION_CONFLICT`。数据库使用 `INT NOT NULL DEFAULT 0`。
4. confirm 固定为本地数据库全事务语义，只接受 `atomic=true`。创建最终错题、确认映射以及临时题变为 SAVED 必须全部成功或全部回滚，不允许 partial success；IGNORED/INVALID 或请求中 `save=false` 的题目不得自动视为 SAVED。
5. `ai_extraction_confirmation` 以 `(task_id,idempotency_key)` 唯一，`request_hash` 是规范化请求载荷的 SHA-256 十六进制摘要。相同 key 和相同 hash 的 COMPLETED 请求直接返回首次 `result_json`；相同 key 配合不同 hash 返回 `409 IDEMPOTENCY_CONFLICT`。
6. `ai_extraction_confirmation_item` 固定临时题与最终错题的一对一关系。状态仅使用 PROCESSING、COMPLETED；失败事务整体回滚，不持久化 FAILED 伪结果。
7. correction 使用 `(task_id,question_id)` 复合外键确保题目属于任务；知识点候选使用 `(extraction_question_id,knowledge_id)` 唯一约束去重。

### Analysis 与计划生成

1. `AiAnalysisDto.status` 复用 `AiTaskStatus`，与 V10 `ai_analysis.status` CHECK 完全一致。成功态继续使用 `SUCCESS`；指令中出现的 `COMPLETED` 按既有冻结枚举校准为 `SUCCESS`，不增加第二个成功编码。
2. `estimatedCost` 使用可空非负 `DECIMAL(18,6)`，`currencyCode` 使用可空三位大写货币代码，不限定具体币种。
3. `generateStudyPlan` 成功受理返回 HTTP 202 和 `AiAnalysisDto`。未来实现只创建 PENDING 分析并立即返回，不得创建占位 StudyPlan，也不得使用模板、随机或无模型回退冒充 AI。
4. Worker 成功时在同一最终落库事务创建 DRAFT StudyPlan、设置 `study_plan.source_analysis_id=ai_analysis.id` 并把分析状态改为 SUCCESS；失败时只改为 FAILED，不创建计划。

V19 新增 `ai_secret`、`ai_extraction_confirmation`、`ai_extraction_confirmation_item`，将业务表从 44 增加到 47；`system_config` 保持 31。本轮不增加纯性能索引，AI analysis 历史组合索引留待真实查询与 EXPLAIN 后评估。

### V3.5.0 冻结验证

Swagger CLI 和 Redocly recommended lint 均通过，Redocly 为 0 errors / 0 warnings。OpenAPI Generator CLI 7.10.0 JAR validate、Java client、TypeScript Fetch 和 Maven Spring 生成均成功；Node 24.18.0 下的 npx wrapper 仍在命令转发前失败，因此不作为契约失败处理。当前为 86 个路径模板、116 个操作、156 个 Schema，operationId 116/116/0，Path Variable 缺口 0。

Testcontainers MySQL 8 已验证空库 V1-V19 和 V18 存量 AI 数据升级，最终版本 v19、47 张业务表、31 项 `system_config`。两份 V19 SHA-256 均为 `C369D3D17A46ED3ECDD512C8F42B63FA49EAC584F1850F7D981BAB8133D081F9`。`clean test` 与 `clean package` 均为 192 项测试通过；V1-V18、Mastery Algorithm V1.0 和非 AI 业务源码未修改。

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

OpenAPI V3.6.0 是阶段十B AI 错题识别开发的契约基线。后续开发必须按 `api/openapi.yaml` 和掌握度算法 V1.0 实现，不得自行猜测字段、状态、响应结构、持久化映射、加密规则、视觉输入转换或计算公式。

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

数据库 V1 至 V18 已冻结，不得回写或重定义既有迁移的业务语义。V19 只完成本节列出的 AI 安全与数据闭环，不修改其他业务域或 system_config；后续数据库变更必须使用新的迁移版本。

## 安全边界

契约已固定 `bearerAuth` 和 JWT Bearer 方案，但当前阶段不代表真实 JWT 鉴权已经实现。鉴权代码、Controller、Service、Entity、前端和真实 AI/文件/恢复执行均不属于本冻结说明的实现内容。

## 验证记录

本轮 `swagger-cli`、Redocly 和 OpenAPI Generator validate 均通过；Java 模型与 TypeScript Fetch 试生成通过。详细统计和一致性结论见 `api/openapi-validation-report.md`。
