# platform 模块任务

> 模块职责：将 runtime 接入 Spring Boot Web 产品，提供 HTTP/SSE、用户上下文、异步执行、配置和 PostgreSQL 持久化
> 当前总体进度：未开始；仅 `apex-agent/pom.xml` 有用户未提交的 PostgreSQL 驱动切换

## PLAT-01 迁移 Spring Boot 装配、Agent 配置与列表接口

- **任务名称**：建立 platform 应用与 SpringPropertiesAgentDefinitionProvider。
- **任务目标**：把现有 Spring 配置映射为完整中立 Agent 定义，并保持 Agent 列表响应兼容。
- **当前进度**：未开始。当前仍使用全局配置 + workspace 配置叠加和旧 Loader；目标配置不兼容迁移该结构。
- **设计依据**：设计文档第 5.7、7.2、15.2 节；架构文档第 5.7、10.1、14 节。
- **涉及范围**：ApexApplication、自动装配、Spring properties/YAML Provider、Bean 到 runtime 注册表适配、`GET /api/sse/agents`。
- **前置依赖**：RUN-01/08、EXT-01。
- **具体执行内容**：
  1. 创建 platform Spring Boot 启动模块并装配一个 ApexAgentRuntime。
  2. 将 Spring 配置转换为单一完整 AgentDefinition，不做字段级叠加。
  3. 移除 default-execution-mode、Plan Prompt、Skill Learning Hook 配置。
  4. 从 Provider `listAgents()` 获取 AgentMetadata 列表，不逐个加载完整定义。
  5. 定义新的完整 YAML 属性结构，不提供现有全局/workspace 配置迁移映射。
- **预期产出**：platform 启动应用、Spring Provider、注册适配和兼容 Agent 列表。
- **验收标准**：
  - platform 能以 Spring Boot 启动并创建单一 runtime。
  - 多个冲突配置源在启动/构造时明确失败。
  - Agent 列表保持 `code/data/message` 及 `agentKey/name` 字段结构。
  - 配置中不存在执行模式、Plan Prompt 或默认 Skill Learning Hook。
- **限制条件或注意事项**：数据库 AgentDefinitionProvider 不实现；SpringProperties Provider 默认使用 YAML，不保留旧配置兼容层；外部路径仍是部署配置，不可提交机器绝对路径。

## PLAT-02 接入 HTTP/SSE、用户上下文与异步执行

- **任务名称**：迁移 chat 入口、请求级 SSE Publisher、409 映射和执行派发。
- **任务目标**：保持现有接口不变，同时让 runtime 的 execution lease 和请求级 Publisher 成为唯一并发/END 正确性来源。
- **当前进度**：未开始。当前 Coordinator 持有独立运行状态，`SuperAgentContext` 持有 SseEmitter。
- **设计依据**：设计文档第 13、15.1、15.3、21.6 节；架构文档第 10 节。
- **涉及范围**：ChatController、ChatService、ApexAgentCoordinator、SseEmitterAgentEventPublisher、X-User-Id Filter、TaskDecorator、异步 executor 和异常映射。
- **前置依赖**：RUN-04A～04C、RUN-08、PRO-02、CORE-07C。
- **具体执行内容**：
  1. 保持 `GET /api/sse/agents`、`POST /api/sse/chat`、`X-User-Id` 和 ChatRequest 字段不变。
  2. 每次 NEW/HUMAN_RESPONSE 新建 SseEmitter 和 Publisher。
  3. Controller 返回 emitter 前同步调用 runtime newAgent/resumeAgent；SessionBusyException 映射 409。
  4. 取得 execution 后再异步 `run()`；线程池拒绝调用 `cancelBeforeStart()`。
  5. Filter/TaskDecorator 传播并清理用户上下文；传给 core/runtime 的命令显式携带 userId。
  6. 删除 platform 私有 runningAgents/sessionLocks 正确性状态。
- **预期产出**：兼容 HTTP/SSE 入口、请求级 Publisher、用户上下文和异步执行集成测试。
- **验收标准**：
  - session 冲突在响应提交前返回 HTTP 409。
  - NEW 与 HUMAN_RESPONSE 使用同一 lease 空间，但各有新 emitter/Publisher。
  - 并发不同 session 事件不串写；每次传输 END 恰好一次。
  - 线程池拒绝时 END 与 lease 各收口一次。
  - 用户上下文在请求/异步结束后清理，core/runtime 不依赖 ThreadLocal。
- **限制条件或注意事项**：platform 不维护第二套锁；本期仅单实例部署；`ApexAgentContext` 不得持有 SseEmitter。

## PLAT-03A 建立 PostgreSQL 配置与 Flyway schema

- **任务名称**：切换唯一数据库并建立三张核心表。
- **任务目标**：完成 PostgreSQL 驱动、数据源和不使用 JSONB 的 Flyway schema。
- **当前进度**：部分具备。根 POM 有用户未提交的 PostgreSQL 驱动切换；尚未迁入 platform 子 POM，也没有 schema。
- **设计依据**：设计文档第 16.1～16.3 节；架构文档第 11.2～11.3 节。
- **涉及范围**：platform POM、dev 数据源、Flyway、`apex_agent_session`、`apex_agent_dialogue_message`、`apex_agent_dialogue_summary`。
- **前置依赖**：FND-02、PLAT-01。
- **具体执行内容**：
  1. 将现有 PostgreSQL 驱动修改迁入 platform POM，移除 MySQL 依赖和 dev MySQL 配置。
  2. 创建三张目标表，不创建独立 Turn/Iteration 表，不保存 current_iteration_no。
  3. 快照、集合、payload 和长内容使用 TEXT；可查询/排序字段提升为标量列。
  4. 时间列统一 `*_time`，消息建立 `(session_id, sort_no)` 唯一约束。
- **预期产出**：PostgreSQL 配置、Flyway migration 和 schema 规则测试。
- **验收标准**：
  - schema 无 JSONB、`apex_agent_turn`、`apex_agent_iteration`、`current_iteration_no`。
  - platform 依赖树无 MySQL 驱动。
  - Flyway 可在空 PostgreSQL 实例上完成迁移。
- **限制条件或注意事项**：不兼容旧 MySQL 配置和历史数据；不得把快照列改为 JSONB。

## PLAT-03B 实现 Session/Conversation Repository Adapter

- **任务名称**：实现 PostgreSQL Repository 与版本化序列化适配。
- **任务目标**：把中立快照、消息和摘要可靠映射到 PLAT-03A schema。
- **当前进度**：未开始。
- **设计依据**：设计文档第 14.6、16.2～16.3 节；架构文档第 11.1～11.3 节。
- **涉及范围**：PostgreSQL SessionRepository、ConversationRepository、数据库实体/Mapper、JsonUtils、版本化 Snapshot Adapter。
- **前置依赖**：PLAT-03A、COM-03/04、EXT-01。
- **具体执行内容**：
  1. 实现 SessionSnapshot、enabledTools、activatedSkills、runtime snapshot 和 suspended tool 的 TEXT 往返。
  2. 实现对话消息有序追加、读取、压缩标记和摘要保存。
  3. 数据库实体不泄漏到 core，读取后必须转换为明确 common 类型。
  4. 覆盖长消息、长摘要、长工具结果和嵌套快照。
  5. 实现首版 `1.0.0` Snapshot Adapter 和 round-trip；不实现跨版本升级链或未知版本分支。
- **预期产出**：两个 PostgreSQL Repository Adapter 和往返集成测试。
- **验收标准**：
  - 全部 TEXT 载荷按明确类型往返且不截断。
  - 消息排序和唯一约束行为正确。
  - core/runtime 不引用 ORM 实体或数据库类型。
  - `1.0.0` 快照版本被明确保存并可完整往返。
- **限制条件或注意事项**：本期不宣称跨版本快照兼容；单 Repository 操作可以使用本地事务，但不扩展为跨 Repository 事务。

## PLAT-03C 实现单 Repository 状态提交与跨 Repository 顺序编排

- **任务名称**：实现设计规定的存储调用顺序和失败停止。
- **任务目标**：在不增加组合事务端口的前提下，落实新 Turn、挂起、压缩、ToolResult 和结束状态的持久化顺序。
- **当前进度**：未开始。
- **设计依据**：设计文档第 2.35、16.4 节；架构文档第 11.4 节。
- **涉及范围**：Repository Adapter 单次操作、core 调用的 platform 集成验证、故障注入测试。
- **前置依赖**：PLAT-03B、CORE-03、CORE-05C、CORE-06、CORE-07A。
- **具体执行内容**：
  1. 验证新 Turn 按“用户消息 → SessionSnapshot”顺序执行。
  2. 验证人工介入状态由一次 SessionRepository save 完整保存。
  3. 验证压缩按“ConversationRepository → SessionRepository → 模型”执行。
  4. 验证 ToolResult 按“ConversationRepository → SessionRepository → 后序 ToolCall”执行。
  5. 对每一步注入失败，确认后续动作停止并传播错误。
- **预期产出**：提交顺序集成测试、单 Repository 一致性测试和失败传播记录。
- **验收标准**：
  - 四类路径的调用顺序和停止条件可客观断言。
  - SessionRepository 单次快照保存、ConversationRepository 单次写入各自保持一致。
  - 测试明确不要求两个 Repository 原子回滚或补偿。
- **限制条件或注意事项**：本期暂不考虑跨 Repository 事务；不得引入 Spring TransactionTemplate 到 core，也不得新增 UnitOfWork/组合 Repository。

## PLAT-03D 完成进程重启恢复测试

- **任务名称**：验证 PostgreSQL 持久化后的挂起和连续会话恢复。
- **任务目标**：证明 platform 进程重启后仍可从已成功保存的状态恢复原 Turn/Iteration/ToolCall。
- **当前进度**：未开始。
- **设计依据**：设计文档第 10、16、21.6 节；架构文档第 8.5、11 节。
- **涉及范围**：Testcontainers PostgreSQL、runtime/platform 重建、HUMAN_RESPONSE、多 ToolCall、长 TEXT 样本。
- **前置依赖**：PLAT-03C、CORE-07C、RUN-04C。
- **具体执行内容**：
  1. 创建并挂起一次执行，销毁并重建 platform/runtime，再提交 HUMAN_RESPONSE。
  2. 验证定义快照、enabledTools、activatedSkills、Hook ID 和前序 ToolResult 恢复。
  3. 覆盖再次挂起、拒绝、批准和全部 CONTINUE。
  4. 覆盖已成功提交边界上的长 TEXT 数据。
- **预期产出**：进程重启恢复集成测试和测试数据集。
- **验收标准**：
  - 重启后恢复同一 Turn/Iteration，不重新执行 AGENT_BUILD 或模型前生命周期。
  - 多 ToolCall 前序结果、挂起 Hook 进度和 session 状态保持。
  - `1.0.0` 快照可在进程重启后恢复；未知版本不属于本期验收范围。
- **限制条件或注意事项**：只验证已成功完成各 Repository 保存后的恢复；不把跨 Repository 部分提交场景描述为原子回滚成功。

## PLAT-04 完成平台协议、并发与前端兼容验收

- **任务名称**：执行 platform 端到端和现有前端零修改验证。
- **任务目标**：证明新后端可无缝替换当前后端，同时明确单实例部署边界。
- **当前进度**：未开始。
- **设计依据**：设计文档第 21.6～22 节；架构文档第 13、15.2、18.4 节。
- **涉及范围**：Controller 集成测试、SSE Golden File、PostgreSQL 测试、前端 test/typecheck/build、部署说明。
- **前置依赖**：PLAT-01/02、PLAT-03A～03D、PRO-02、RUN-08。
- **具体执行内容**：
  1. 覆盖 NEW/HUMAN_RESPONSE、用户校验、409、线程池拒绝、每请求 emitter 隔离。
  2. 对实际 SSE 运行 FND-01/PRO-02 Golden File。
  3. 覆盖正常、失败、挂起、再次挂起和重启恢复。
  4. 覆盖模型异常直接失败、Hook 异常 warn 后跳过、工具异常回传模型和最大 Iteration 强制收口。
  5. 在不修改 `apex-frontend/src` 的前提下运行现有前端测试、typecheck、build。
  6. 在部署文档中声明单实例，禁止把共享 PostgreSQL 描述为分布式 lease。
- **预期产出**：平台验收测试、前端兼容记录和单实例部署说明。
- **验收标准**：
  - Controller 路径、Header、请求字段和响应结构与基线一致。
  - SSE Golden File 全部通过，END 精确且仅一次。
  - 前端 `test:run`、`typecheck`、`build` 全部通过且 `git diff -- apex-frontend/src` 为空。
  - 单实例限制在配置/部署文档中可见。
- **限制条件或注意事项**：不得为了通过验证修改前端源码；快照只验收 `1.0.0`，不扩展跨版本或未知版本场景。
