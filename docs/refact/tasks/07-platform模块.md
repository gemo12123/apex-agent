# platform 模块任务

> 模块职责：将 runtime 接入 Spring Boot Web 产品，提供 HTTP/SSE、用户上下文、异步执行、配置和 PostgreSQL 持久化
> 当前总体进度：已完成（2026-08-04）；生产实现、非 PG 自动化、前端与依赖门禁通过，PostgreSQL/Testcontainers 因无 Docker 未实测并按本轮用户环境豁免接受，达到 G5

## PLAT-01 迁移 Spring Boot 装配、Agent 配置与列表接口

- **任务名称**：建立 platform 应用与 SpringPropertiesAgentDefinitionProvider。
- **任务目标**：把现有 Spring 配置映射为完整中立 Agent 定义，并保持 Agent 列表响应兼容。
- **当前进度**：已完成（2026-08-02）。platform 已可启动单一 runtime，并通过完整 YAML Provider 装配 Agent 列表；未保留旧 global/workspace 配置兼容层。
- **设计依据**：设计文档第 5.7、7.2、15.2 节；架构文档第 5.7、10.1、14 节。
- **涉及范围**：ApexApplication、自动装配、Spring properties/YAML Provider、Bean 到 runtime 注册表适配、`GET /api/sse/agents`。
- **前置依赖**：RUN-01/08、EXT-01。
- **具体执行内容**：
  1. 创建 platform Spring Boot 启动模块并装配一个 ApexAgentRuntime。
  2. 将 Spring 配置转换为单一完整 AgentDefinition，不做字段级叠加。
  3. 移除 default-execution-mode、Plan Prompt、Skill Learning Hook 配置。
  4. 从 Provider `listAgents()` 获取 AgentMetadata 列表，不逐个加载完整定义。
  5. 定义新的完整 YAML 属性结构；不读取、不转换旧 global/workspace 配置，不提供兼容层、迁移脚本或人工转换清单。
- **预期产出**：platform 启动应用、Spring Provider、注册适配和兼容 Agent 列表。
- **验收标准**：
  - platform 能以 Spring Boot 启动并创建单一 runtime。
  - 多个冲突配置源在启动/构造时明确失败。
  - Agent 列表保持 `code/data/message` 及 `agentKey/name` 字段结构。
  - 配置中不存在执行模式、Plan Prompt 或默认 Skill Learning Hook。
- **限制条件或注意事项**：数据库 AgentDefinitionProvider 不实现；SpringProperties Provider 默认使用 YAML，不保留旧配置兼容层；外部路径仍是部署配置，不可提交机器绝对路径。

## PLAT-02 接入 HTTP/SSE、用户上下文与异步执行

- **任务名称**：迁移 chat 入口、请求级 SSE Publisher、409 映射和执行派发。
- **任务目标**：保持既有 chat/agents 接口不变，让 runtime 的 execution lease 和请求级 Publisher 成为唯一并发/END 正确性来源，并新增刷新挂起交互所需的只读会话状态查询。
- **当前进度**：已完成（2026-08-02）。已接入兼容 chat/agents 入口、请求级 SSE Publisher、用户上下文、异步执行、runtime lease/取消/Once Publisher 与 Q-14 只读查询；platform 未新增 session 锁或 END 状态。
- **设计依据**：设计文档第 13、15.1、15.3、21.6 节；架构文档第 10 节。
- **涉及范围**：ChatController、ChatService、SessionStateController/QueryService/ViewMapper、ApexAgentCoordinator、SseEmitterAgentEventPublisher、X-User-Id Filter、TaskDecorator、异步 executor 和异常映射。
- **前置依赖**：RUN-04A～04C、RUN-08、PRO-02、CORE-07C。
- **具体执行内容**：
  1. 保持 `GET /api/sse/agents`、`POST /api/sse/chat`、`X-User-Id` 和 ChatRequest 字段不变。
  2. 每次 NEW/HUMAN_RESPONSE 新建 SseEmitter 和 Publisher。
  3. Controller 返回 emitter 前同步调用 runtime newAgent/resumeAgent；SessionBusyException 映射 409；其他 core 同步构造/恢复准备异常返回 runtime 已收口的仅 END SSE。
  4. 取得 execution 后再异步 `run()`；线程池拒绝调用 `cancelBeforeStart()`。
  5. Filter/TaskDecorator 传播并清理用户上下文；传给 core/runtime 的命令显式携带 userId。
  6. 删除 platform 私有 runningAgents/sessionLocks 正确性状态。
  7. emitter completion/timeout/error 通过请求级 execution 调用非阻塞 `cancel()`；用 closed 标志和 execution 原子引用的绑定后二次检查，覆盖 callback 先发生的竞态。
  8. END 发布只 send；由 execution terminator 在状态切为 TERMINATED 后 complete emitter，避免正常完成回调反向触发取消。
  9. 新增 `GET /api/sse/sessions/{sessionId}?agentKey=...`；按 Header userId、agentKey、sessionId 校验归属，从 SessionSnapshot 映射 status 和既有 ASK_HUMAN/TOOL_CONFIRMATION，不调用 runtime 执行入口或 lease。
- **预期产出**：兼容 HTTP/SSE 入口、请求级 Publisher、用户上下文和异步执行集成测试。
- **验收标准**：
  - session 冲突在响应提交前返回 HTTP 409，且不发送 END。
  - Header/字段非法返回 400；core 构造/恢复准备失败返回 HTTP 200 `text/event-stream`，消息序列严格等于单个既有 END。
  - NEW 与 HUMAN_RESPONSE 使用同一 lease 空间，但各有新 emitter/Publisher。
  - 并发不同 session 事件不串写；每次传输 END 恰好一次。
  - 线程池拒绝时 END 与 lease 各收口一次。
  - emitter 在 execution 绑定前或 RUNNING 期间关闭都能发出一次取消命令；callback 不直接发布 END 或释放 lease。
  - 正常 END/complete 时 completion callback 不触发 token，执行 outcome 不被改成 CANCELLED。
  - 用户上下文在请求/异步结束后清理，core/runtime 不依赖 ThreadLocal。
  - 状态查询对不存在、跨用户或 agentKey 不匹配统一 404；HITL 返回原挂起交互，其他状态 pending 为空，且无 Hook/模型/工具/Publisher/lease 调用。
- **限制条件或注意事项**：platform 不维护第二套锁；本期仅单实例部署；`ApexAgentContext` 不得持有 SseEmitter。

## PLAT-03A 建立 PostgreSQL 配置与 Flyway schema

- **任务名称**：切换唯一数据库并建立三张核心表。
- **任务目标**：完成 PostgreSQL 驱动、数据源和不使用 JSONB 的 Flyway schema。
- **当前进度**：已完成（2026-08-04，含环境豁免）。PostgreSQL/Flyway 配置、三表 migration、schema 规则和无 MySQL 依赖均已验证；空库 Testcontainers 因无 Docker 未实测，按本轮用户明确豁免接受但不记为通过。
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
- **当前进度**：已完成（2026-08-04，含环境豁免）。Adapter、`1.0.0` TEXT 快照和内存往返已验证；真实 PostgreSQL 长 TEXT、幂等与约束因无 Docker 未实测，按本轮用户豁免接受但不记为通过。
- **设计依据**：设计文档第 14.6、16.2～16.3 节；架构文档第 11.1～11.3 节。
- **涉及范围**：PostgreSQL SessionRepository、ConversationRepository、数据库实体/Mapper、JsonUtils、版本化 Snapshot Adapter。
- **前置依赖**：PLAT-03A、COM-03/04、EXT-01。
- **具体执行内容**：
  1. 实现 SessionSnapshot、enabledTools、activatedSkills、runtime snapshot 和 suspended tool 的 TEXT 往返。
  2. 实现对话消息有序追加、读取、压缩标记和摘要保存。
  3. 数据库实体不泄漏到 core，读取后必须转换为明确 common 类型。
  4. 覆盖长消息、长摘要、长工具结果和嵌套快照。
  5. 实现首版 `1.0.0` Snapshot Adapter 和 round-trip；不实现跨版本升级链或未知版本分支。
  6. 保证读取快照能取得刷新查询所需的 userId、agentKey、executionStatus 与完整 suspended intervention，不另建重复状态表。
- **预期产出**：两个 PostgreSQL Repository Adapter 和往返集成测试。
- **验收标准**：
  - 全部 TEXT 载荷按明确类型往返且不截断。
  - 消息排序和唯一约束行为正确。
  - core/runtime 不引用 ORM 实体或数据库类型。
  - `1.0.0` 快照版本被明确保存并可完整往返。
  - 两类挂起 interaction 从 PostgreSQL 往返后仍可映射为与实时事件一致的 protocol 消息。
- **限制条件或注意事项**：本期不宣称跨版本快照兼容；单 Repository 操作可以使用本地事务，但不扩展为跨 Repository 事务。

## PLAT-03C 实现单 Repository 状态提交与跨 Repository 顺序编排

- **任务名称**：实现设计规定的存储调用顺序和失败停止。
- **任务目标**：在不增加组合事务端口的前提下，落实新 Turn、挂起、压缩、ToolResult 和结束状态的持久化顺序。
- **当前进度**：已完成（2026-08-02）。Adapter 仅使用单 Repository 本地事务，跨 Repository 顺序继续由 core 编排；未引入 UnitOfWork、组合事务或 `TransactionTemplate`，顺序与失败停止由 core 回归测试覆盖。
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
- **当前进度**：已完成（2026-08-04，含环境豁免）。重启恢复 Testcontainers 已实现；当前环境无 Docker 因而未实测，按本轮用户明确豁免接受但不记为通过。
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

## PLAT-04 完成平台协议、并发与前端刷新兼容验收

- **任务名称**：执行 platform 端到端、既有契约兼容与前端刷新回显验证。
- **任务目标**：证明新后端保持既有聊天契约，并能在刷新后重新展示持久化的人工介入，同时明确单实例部署边界。
- **当前进度**：已完成（2026-08-04）。HTTP/SSE、400、409、END-only、取消、Q-14、前端刷新回显、全量测试、typecheck 和 build 已验证；PostgreSQL/重启因无 Docker 未实测并按本轮用户环境豁免接受，达到 G5，但不将跳过项写为通过。
- **设计依据**：设计文档第 21.6～22 节；架构文档第 13、15.2、18.4 节。
- **涉及范围**：Controller 集成测试、SSE Golden File、PostgreSQL 测试、前端 test/typecheck/build、部署说明。
- **前置依赖**：PLAT-01/02、PLAT-03A～03D、PRO-02、RUN-08。
- **具体执行内容**：
  1. 覆盖 NEW/HUMAN_RESPONSE、用户校验、409、线程池拒绝、每请求 emitter 隔离。
  2. 对实际 SSE 运行 FND-01/PRO-02 Golden File。
  3. 覆盖正常、失败、挂起、再次挂起、刷新查询和重启恢复。
  4. 覆盖模型异常直接失败、Hook 异常 warn 后跳过、工具异常回传模型和最大 Iteration 强制收口。
  5. 前端以 `apex:active-session:v1` 持久化当前 `{userId, agentKey, sessionId}`，只在等待人工态保留；初始化查询会话状态，HITL 交互交给既有 reducer 重新展示，终态/切用户/损坏/404 清除、5xx 保留并可重试，且不自动提交 HUMAN_RESPONSE。
  6. 在部署文档中声明单实例，禁止把共享 PostgreSQL 描述为分布式 lease。
  7. 运行前端测试、typecheck、build，并审查前端改动仅限状态 DTO、查询 API、session 定位和初始化回显。
- **预期产出**：平台验收测试、前端兼容记录和单实例部署说明。
- **验收标准**：
  - 既有 Controller 路径、Header、请求字段和响应结构与基线一致；新增只读状态接口通过归属与无副作用测试。
  - SSE Golden File 全部通过，END 精确且仅一次。
  - 前端 `test:run`、`typecheck`、`build` 全部通过；刷新后两类 HITL 卡片回显且既有 chat/SSE reducer 测试无回归。
  - 单实例限制在配置/部署文档中可见。
- **限制条件或注意事项**：前端只允许 Q-14 所需的最小增量，不新增完整历史查询或改变既有 SSE 事件；快照只验收 `1.0.0`，不扩展跨版本或未知版本场景。

## 2026-08-02 本轮执行记录

- 已落地 Spring Boot 装配、完整 Agent YAML/Properties Provider、兼容 agents/chat 接口、请求级 SSE Publisher、用户上下文与异步执行。
- 已落地 Q-14 只读会话状态查询，以及前端 `apex:active-session:v1` 定位、两类挂起交互刷新回显、终态/404 清理和 5xx 保留重试语义。
- 已切换 platform PostgreSQL 驱动、数据源与 Flyway 三表 schema，并实现 Session/Conversation Repository Adapter、`1.0.0` TEXT 快照适配和单实例部署说明；不兼容旧 MySQL 配置或数据。
- `mvn -f apex-agent/pom.xml test`：通过；platform 15 项测试中 14 项通过、1 项 PostgreSQL/Testcontainers 测试因无 Docker 跳过。
- `mvn -pl platform -am verify -DskipTests`：通过，依赖声明无问题；`mvn -pl platform -am dependency:tree "-Dincludes=mysql:*"`：通过且无 MySQL 条目。
- 前端 Q-14 定向测试：3 个文件 19 项通过；`npm run test:run`：13 个文件 48 项通过；`npm run typecheck`、`npm run build` 通过。
- 阻塞：需要可用 Docker/PostgreSQL 环境执行 Flyway、持久化与进程重启 HUMAN_RESPONSE 恢复测试。该项通过前，PLAT-04、G5 和 platform“已完成”状态均保持未达成。
