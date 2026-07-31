# runtime 模块任务

> 模块职责：提供无需 Spring IoC、可通过 Builder 直接运行的默认实现和外部集成
> 当前总体进度：未开始；现有运行能力仍由 Spring 单模块装配

## RUN-01 实现 ApexAgentRuntime Builder、注册表与定义 Provider

- **任务名称**：建立 runtime 公共 API 和基础装配。
- **任务目标**：外部项目只依赖 runtime，提供 ChatModel/ModelGateway 与 Agent 定义后即可通过普通 Java `new`/Builder 运行。
- **当前进度**：未开始。
- **设计依据**：设计文档第 5.6、7.2、14.1～14.3 节；架构文档第 5.6、9.1～9.2、14 节。
- **涉及范围**：`ApexAgentRuntime`、Builder、Tool/Hook/Skill 注册表、Programmatic/FileAgentDefinitionProvider、默认 Agent/Prompt、静态预检入口。
- **前置依赖**：CORE-01～04、KIT-01～03、EXT-01/02。
- **具体执行内容**：
  1. 提供 `chatModel`/`modelGateway`、AgentDefinition/Provider、Repository、PublisherFactory、Coordinator 和注册 API。
  2. Builder 校验必需端口、单一 Provider、注册名去重、Hook 类型元数据和资源所有权。
  3. 补齐默认 ReAct Agent、Prompt、maxIterations=30、kit 工具、内存 Store、Print Publisher 工厂。
  4. Builder 不加载动态定义、不校验定义级工具/Hook/Skill关系。
  5. 静态预检复用 CORE-01 唯一校验器。
  6. Programmatic/File Provider 均实现 `listAgents()`；File Provider 接收调用方显式指定的 classpath 或文件系统 YAML 资源，初始化时加载一次并缓存，不扫描目录、不热加载。
- **预期产出**：runtime Builder/API、注册表、Java/文件 Provider 和无 Spring 示例。
- **验收标准**：
  - 不创建 Spring ApplicationContext 即可运行一次无工具 Agent。
  - 多 Provider、重名注册和缺少必需端口在 build 阶段明确失败。
  - 动态 Provider 在 build 阶段调用次数为 0，请求期由 core Assembler 调用。
  - File Provider 可从单个 YAML 资源加载完整的多 Agent 定义和元数据列表，文件变化不会影响已创建 Provider。
  - 默认配置不启动 MCP、SubAgent 或外部 Skill 资源。
- **限制条件或注意事项**：不实现数据库 Provider；不保留全局 + workspace 字段级叠加；不设计现有配置迁移、目录发现或热加载能力。

## RUN-02 实现 Spring AI 中立模型与工具适配

- **任务名称**：适配 ChatModel、消息、流响应和 ToolCallback。
- **任务目标**：隔离 Spring AI 类型，保证 common ModelRequest/Response 与真实模型调用之间无损转换。
- **当前进度**：未开始。
- **设计依据**：设计文档第 5.6、23.1 节；架构文档第 5.6、17.1 节。
- **涉及范围**：ModelGateway、Spring AI Message/ChatResponse/ToolCall/ToolResponse 转换、流 observer、模型工具定义适配。
- **前置依赖**：COM-01/04、EXT-01、CORE-05A/05B、CORE-06。
- **具体执行内容**：
  1. 建立 common 与 Spring AI 消息的双向 Adapter。
  2. 保留 ToolCall ID、名称、参数、顺序、role、内容和必要 metadata。
  3. 将流式内容通过 observer 交给 core 事件工厂，并返回完整中立 ModelResponse。
  4. 把 AgentTool 适配为模型可见工具定义，关闭 Spring AI 自动工具执行；真实执行仍由 CORE-06 统一调用 AgentTool。
  5. 对真实 Spring AI 消息样本做 round-trip 契约测试。
  6. subscription 建立后立即向请求级 token 注册 dispose；token 在建立前或建立后取消都必须终止活动模型调用。
- **预期产出**：Spring AI Adapter、默认 ModelGateway、模型工具定义适配器及契约测试。
- **验收标准**：
  - 文本、工具调用、多工具顺序和 ToolResult round-trip 无信息丢失。
  - core/common 不出现 Spring AI import。
  - ModelGateway 内部重试不重复进入 core 压缩门。
  - 请求取消会主动 dispose 模型 subscription，并以 `CancellationRequestedException` 结束而不是记为模型失败。
- **限制条件或注意事项**：不升级 Spring AI 版本；若现有供应商专有字段无法由已设计中立模型表达，标记设计缺口而非把供应商类型泄漏到 common。

## RUN-03 实现内存存储、对话窗口与默认压缩能力

- **任务名称**：提供 runtime 默认 Session/Conversation Repository 和压缩实现。
- **任务目标**：支持同一 runtime 实例内的连续会话、挂起恢复、窗口准备和业务模型调用前摘要压缩。
- **当前进度**：未开始。现有会话 Store 位于 memory 包且对象隔离不足以代表目标快照契约。
- **设计依据**：设计文档第 5.6、9.2、14.5、21.4～21.5 节；架构文档第 9.4、11.1 节。
- **涉及范围**：InMemorySessionRepository、InMemoryConversationRepository、ConversationWindowManager、默认 CompactionPolicy/Compactor。
- **前置依赖**：COM-03/04、EXT-01/02、CORE-03、CORE-05C。
- **具体执行内容**：
  1. 按中立快照语义实现内存 Session/Conversation 存储。
  2. save/load 两侧深拷贝或保存不可变快照。
  3. 实现对话窗口、token/字符/消息数判断和保留窗口参数。
  4. Compactor 自行限制分片输入，不调用 ApexAgent 业务模型入口。
  5. 支持 ConversationRepository 内部单次压缩操作的一致性；与 SessionRepository 的组合只遵循 CORE-05C 的调用顺序，不提供跨 Repository 原子事务。
- **预期产出**：内存 Repository、窗口/压缩默认实现和别名/顺序测试。
- **验收标准**：
  - 保存后修改原对象不影响存储；修改 load 返回值不影响后续 load。
  - 嵌套 Turn/Iteration/ToolCall/Map/集合均隔离。
  - 压缩 false/true/失败与超长分片有测试；内部模型调用不递归触发 core 压缩门。
  - 同一 runtime 实例可完成挂起和恢复；实例销毁后不承诺恢复。
- **限制条件或注意事项**：本任务不实现长期 Memory；默认估算算法是实现细节，但不得只统计历史消息，必须包含 system prompt 和启用工具定义。

## RUN-04A 实现 ApexAgentExecution 状态机

- **任务名称**：实现执行、取消和关闭的请求级状态机。
- **任务目标**：用原子状态保证 execution 只能启动一次，且所有结束路径只执行一次资源收口。
- **当前进度**：未开始。
- **设计依据**：设计文档第 13.3、14.4 节；架构文档第 8.6、9.3 节。
- **涉及范围**：ApexAgentExecution、RuntimeCancellationSource、ActiveExecutionRegistry、run、cancel、cancelBeforeStart、close、构造失败和状态转换测试。
- **前置依赖**：CORE-04、RUN-01。
- **具体执行内容**：
  1. 定义 PREPARED、RUNNING、CANCEL_REQUESTED、TERMINATED 状态及合法转换。
  2. `run()` 只能成功进入一次，finally 统一调用幂等收口。
  3. 提供请求级 `cancel()`：未启动时调用 core `cancelBeforeRun()` 保存 CANCELLED 后立即收口；运行中只发出 token 取消命令并立即返回，不等待、不提前发布 END 或释放 lease。
  4. `cancelBeforeStart()` 只在未启动时生效，发布结束请求并释放资源。
  5. `close()` 委托 `cancel()`，运行中也会收到取消命令。
  6. `RuntimeCancellationSource` 向 common 暴露只读 token，并保证取消前后注册的底层 command 都精确执行一次。
  7. execution 返回调用方前登记到活动表，终止回调按 END、请求资源、lease、注销顺序幂等收口。
  8. 终止时先把 execution 原子置为 TERMINATED，再执行 END/complete 等外部回调，避免 emitter completion 反向取消正常执行。
  9. 为 Publisher 异常、Agent 构造失败、正常、失败、再次挂起和运行中取消路径建立竞态测试。
- **预期产出**：ApexAgentExecution 状态机和并发单元测试。
- **验收标准**：
  - run/cancel/close 并发竞争时 Agent 至多运行一次，收口回调至多执行一次。
  - 未调用 run 时 close 把已准备 Session/Turn 记为 CANCELLED 并释放；运行后重复 close 无副作用。
  - 非法状态转换有明确异常或幂等结果。
  - RUNNING 状态 cancel/close 立即返回并触发底层 callback；lease 只在 run finally 释放。
- **限制条件或注意事项**：`cancel()` 返回 true 只表示首次发出命令；若 core 已提交自然终态，迟到取消不回写 CANCELLED。测试按线性化顺序接受自然终态或取消终态，但资源收口必须唯一。本任务通过抽象收口回调测试，不实现具体 lease 表或 Once Publisher；不设置取消超时或 grace period，也不保证不合作的自定义工具在有限时间内退出。

## RUN-04B 实现 SessionExecutionCoordinator 与 lease

- **任务名称**：实现单进程 session execution lease。
- **任务目标**：让 NEW/HUMAN_RESPONSE 在 runtime API 返回前同步竞争同一 sessionId 占用空间。
- **当前进度**：未开始。当前锁主要位于 Web Coordinator。
- **设计依据**：设计文档第 2.29、2.31、14.4 节；架构文档第 9.3 节。
- **涉及范围**：SessionExecutionCoordinator、SessionExecutionLease、SessionBusyException、稳定 LockEntry/引用计数。
- **前置依赖**：RUN-01。
- **具体执行内容**：
  1. 实现同步 acquire 和幂等 lease release。
  2. NEW/HUMAN_RESPONSE 共用 sessionId 锁空间。
  3. 管理稳定 LockEntry，持有者或等待者存在时不删除并重建同 key 锁。
  4. 覆盖同 session 冲突、不同 session 并行、释放后重入和高并发清理。
- **预期产出**：lease SPI 默认实现和并发测试。
- **验收标准**：
  - 第二个同 session acquire 同步抛出 SessionBusyException。
  - 不同 session 可并行，释放后同 session 可再次获取。
  - 任一 lease 多次 release 只生效一次。
- **限制条件或注意事项**：默认实现只保证单 runtime 进程；本期 platform 只能单实例，不能用共享 PostgreSQL 替代分布式 lease。

## RUN-04C 实现请求级 Once Publisher 并集成 execution/lease

- **任务名称**：完成请求级事件隔离和 runtime new/resume 同步准备。
- **任务目标**：为每次 NEW/HUMAN_RESPONSE 创建独立事件出口，并把 Publisher、core Agent 和 lease 交给 RUN-04A 状态机统一持有。
- **当前进度**：未开始。
- **设计依据**：设计文档第 2.28～2.29、13.1～13.3、14.4 节；架构文档第 8.6、9.1、9.3 节。
- **涉及范围**：OnceAgentEventPublisher、AgentEventPublisherFactory、Print Publisher、runtime newAgent/resumeAgent、RUN-04A/04B 集成。
- **前置依赖**：RUN-04A、RUN-04B、CORE-01、CORE-04、EXT-01。
- **具体执行内容**：
  1. 显式 Publisher 绑定本次请求；缺省时 Factory 每次创建独立 Print Publisher。
  2. 用请求级 Once 装饰器保证父请求 END 实际只发布一次。
  3. runtime 先 acquire lease，再调用 core Factory，最后返回持有三者的 ApexAgentExecution。
  4. core 同步构造/恢复准备失败时通过同一 Once Publisher 发布且只发布精确 END，释放 lease 后抛出带 `endPublished=true` 的准备异常供 platform 返回已完成 emitter。
  5. 让 ToolExecutionObserver 最终写入当前请求的 Once Publisher，但禁止工具发布 END。
  6. 覆盖并发不同 session、连续恢复、构造失败、线程池拒绝和 Publisher 异常。
  7. 每个 execution 创建唯一 `RuntimeCancellationSource`，并向 ModelStreamObserver、ToolExecutionObserver、ToolExecutionContext 注入同一 token；Publisher 失败触发该 source。
- **预期产出**：请求级 Publisher、runtime 准备 API 和端到端并发测试。
- **验收标准**：
  - newAgent/resumeAgent 返回前已取得 lease；冲突同步抛出。
  - 每次 NEW/HUMAN_RESPONSE 使用独立 Publisher 和 END 状态。
  - 正常、失败、挂起、构造失败和 cancelBeforeStart 均只发送一次 END、释放一次 lease；构造/恢复准备失败流中不存在 END 以外的事件。
  - ToolExecutionObserver 进度事件不串请求且不能结束父传输。
  - execution 返回前已登记，准备失败无残留登记；Publisher 失败触发 token，finally 后活动表清零。
- **限制条件或注意事项**：Builder 不接受共享有状态 Publisher 实例；platform 不得维护第二套 session 锁或 END 状态。

## RUN-05 迁移普通 Skill 加载、激活与资源读取

- **任务名称**：迁移 `org.gemo.apex.skills` 中非 learning 能力。
- **任务目标**：保留文件 Skill 的发现、解析、instructions、资源读取和 `activate_skill` 行为，同时把状态改为 session 隔离。
- **当前进度**：未开始。
- **设计依据**：设计文档第 5.6、12、21.4 节；架构文档第 6.4 节。
- **涉及范围**：非 learning Skill loader/provider、Skill registry、activate_skill、read_skill_resource、消息写入与 session 状态。
- **前置依赖**：RUN-01/03、CORE-03/06、COM-01。
- **具体执行内容**：
  1. 迁移现有非 learning 文件 Skill 行为，不删减发现和资源读取能力。
  2. Agent 定义只配置 enabledSkills，session 保存 activatedSkills。
  3. activate_skill 是唯一默认激活入口；instructions 作为普通 ToolResult 进入对话。
  4. 重复激活幂等且可再次返回 instructions；不同 session/用户隔离。
  5. Skill 被新定义移除时，在下一个普通 NEW 构造阶段清理激活项并告警。
  6. 资源读取只允许 enabledSkills。
- **预期产出**：runtime 普通 Skill 能力和回归测试。
- **验收标准**：
  - activatedSkills 跨 Turn 保留、跨 session/用户不共享。
  - instructions 不作为固定 system 前缀重复注入。
  - 已激活 Skill 可再次返回 instructions。
  - `skills.learning` 不进入 runtime artifact。
- **限制条件或注意事项**：不保留全局 + workspace Skill 叠加；Hook 不动态增删 Skill 集合；Skill Learning 由 memory 任务负责。

## RUN-06 迁移 MCP stdio/SSE 集成

- **任务名称**：把 MCP 客户端和工具适配迁移到 runtime。
- **任务目标**：以普通 AgentTool 暴露 MCP 工具，并由 runtime 管理 stdio/SSE 连接的完整生命周期。
- **当前进度**：未开始。
- **设计依据**：设计文档第 5.6、11.3、23 节相关风险；架构文档第 12.1 节。
- **涉及范围**：McpTransport、stdio 进程、SSE Client、工具发现/适配、超时/重连/关闭、Client 缓存。
- **前置依赖**：RUN-01/02、RUN-04C、CORE-06。
- **具体执行内容**：
  1. 抽象 stdio/SSE transport 并适配为 AgentTool。
  2. 调用只发送最终工具参数，不传 session、用户、Agent 或 ToolExecutionContext。
  3. Client 缓存按 runtime 实例和 server 定义隔离。
  4. 未配置时不启动进程/连接；关闭 runtime 时释放全部自有资源。
  5. server 初始化失败时记录 warn，关闭本次失败产生的资源，并按 server sourceId/稳定工具名前缀登记不可用状态；健康 server 和 runtime 启动继续，不提供策略开关。
  6. 不为不可用工具建立新活动绑定；已有 session 的既有绑定只读留痕并退出有效集合，既有 ToolCall/ToolResult 不删除且不能重放执行。
  7. 每次 MCP call 创建可取消 handle，向 ToolExecutionContext 的 token 注册 cancel；取消时停止重连/读取并抛 `CancellationRequestedException`。
- **预期产出**：可选 MCP 集成、资源生命周期测试和参数泄漏测试。
- **验收标准**：
  - stdio/SSE 工具发现和调用契约测试通过。
  - 发送载荷只含工具参数。
  - runtime close 后进程、连接和调度资源全部关闭。
  - 未配置 MCP 时无相关资源创建。
  - 单个 MCP server 初始化失败不阻止其他 server/runtime 启动；不可用新绑定被拒绝、旧绑定只读留痕，受影响工具不进入模型列表且无资源泄漏。
  - 活动 MCP 调用收到取消命令后 handle.cancel 精确一次，不由 adapter 形成普通失败 ToolResult；core 仅按统一取消收口规则补标准结果。
- **限制条件或注意事项**：不得把 MCP 类型放入 common/core；不可用范围只覆盖失败 server 的 sourceId/稳定前缀，不扩大到健康集成，也不得在恢复健康时自动启用旧 session。

## RUN-07 迁移 HTTP SubAgent 工具

- **任务名称**：把任意远程 Agent 适配为普通 HTTP 工具。
- **任务目标**：复用现有 chat/SSE 协议完成远端调用、流聚合、事件透传和递归防护。
- **当前进度**：未开始。现有实现仍使用 Fastjson 且与旧上下文/事件处理耦合。
- **设计依据**：设计文档第 11.4、23 节；架构文档第 12.2 节。
- **涉及范围**：HTTP 客户端、SSE parser、SubAgent Tool、子 session/调用链、STREAM_CONTENT 聚合、INVOCATION/ARTIFACT 处理。
- **前置依赖**：PRO-01/02、COM-04、EXT-01、RUN-01、RUN-04C、CORE-06。
- **具体执行内容**：
  1. 使用 `POST /api/sse/chat`、RequestType.NEW、目标 agentKey 和独立子 sessionId。
  2. 通过 `X-User-Id` 传播用户身份。
  3. 聚合 STREAM_CONTENT 为父 ToolResult；通过 core 传入的 ToolExecutionObserver 发布 INVOCATION 事件。
  4. 保持 ARTIFACT 当前忽略语义；远端 END 只结束当前工具调用，不调用 observer 发布 END。
  5. 实现业务超时、请求级主动取消、异常转换、最大深度和 agentKey 调用链闭环检测。
  6. 使用 protocol + Jackson 解析，移除 Fastjson。
  7. SubAgent 初始化失败时记录 warn，关闭本次失败产生的资源并按 sourceId/工具名登记不可用状态；新绑定被拒绝，已有绑定只读留痕并退出有效集合，其他工具继续；不提供策略开关。
  8. HTTP future 与 response body/stream 向请求 token 注册 cancel/close；取消时停止读取和事件转发并抛 `CancellationRequestedException`。
- **预期产出**：HTTP SubAgent AgentTool、SSE 解析器和集成测试。
- **验收标准**：
  - 子调用使用独立 session，不复用父 session。
  - 用户身份正确传播，父结果按顺序聚合。
  - INVOCATION 事件经 ToolExecutionObserver 写入当前父请求；远端 END 不会结束父请求。
  - 递归深度和 agentKey 闭环均被拒绝。
  - 单个 SubAgent 初始化失败不阻止 runtime 启动，对应工具不进入模型列表且无资源泄漏。
  - 依赖和源码中不再因该能力引入 Fastjson。
  - token 在发请求前、等待响应和读取 body 阶段取消都能主动终止底层调用，adapter 不生成模型可见失败 ToolResult；core 仅按统一取消收口规则补标准结果。
- **限制条件或注意事项**：SubAgent 不是特殊 Agent 类型；不得改变远端协议；调用链信息只用于 runtime 观测和防递归，不进入前端协议。

## RUN-08 完成资源生命周期、runtime-only 示例与集成验收

- **任务名称**：收口 runtime 默认能力和可选资源所有权。
- **任务目标**：证明 runtime 在无 Spring IoC 情况下可独立运行、恢复、关闭，并且可选集成不会泄漏资源。
- **当前进度**：未开始。
- **设计依据**：设计文档第 14、21.4、22 节；架构文档第 9.5、15.1 节。
- **涉及范围**：ApexAgentRuntime AutoCloseable、ActiveExecutionRegistry、ResourceRegistry、内部 executor/scheduler、示例、runtime 集成测试与 artifact 依赖。
- **前置依赖**：RUN-01～03、RUN-04A～04C、RUN-05～07。
- **具体执行内容**：
  1. 明确 runtime 创建和外部注入资源的所有权、关闭顺序和幂等性。
  2. 未配置可选能力时不创建相关客户端、进程或线程。
  3. 提供最小 Builder、显式 Publisher、HUMAN_RESPONSE 恢复示例。
  4. 运行无 Spring IoC 集成测试，覆盖默认内存、Print JSON、工具、压缩和恢复。
  5. 覆盖 MCP/SubAgent 单项初始化失败，验证 warn、新绑定拒绝、旧绑定转历史、有效集合清理、历史消息保留、健康工具继续可用及失败资源关闭。
  6. 检查 runtime artifact 不依赖 platform/memory。
  7. close 原子拒绝新 execution，向活动表快照逐个发出 `cancel()` 后立即返回；无活动 execution 时同步关闭共享自有资源，有活动 execution 时由最后一个注销触发关闭。
- **预期产出**：runtime-only 示例、集成测试、资源关闭报告。
- **验收标准**：
  - 普通 Java 测试不启动 ApplicationContext 并完整执行一次 Agent。
  - runtime close 多次安全，所有自有线程/客户端/进程释放。
  - 默认 Print 输出满足 protocol Golden File。
  - MCP/SubAgent 初始化失败不会阻止健康 Agent 的 runtime-only 执行；受影响工具不出现在模型列表或 session enabledTools，历史记录不能被执行。
  - 依赖树无 platform/memory，且只有最小 Spring AI 依赖。
- close 与 new/resume 竞态不会产生关闭后漏登记；活动执行的 lease 不被 close 线程提前释放。
- **限制条件或注意事项**：外部注入资源是否由 runtime 关闭必须在 Builder 契约中显式；MCP/SubAgent 初始化失败固定采用 warn、禁止新绑定、旧绑定只读留痕、健康能力继续的策略。取消只要求发出命令，不设置超时或 grace period；不合作的自定义工具可能导致 execution、lease 和依赖它的共享资源长期保留。
