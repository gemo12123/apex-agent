# runtime 模块详细设计

## 模块设计定位

`apex-agent-runtime` 把 core、kit 和默认端口实现装配成无需 Spring IoC 的可运行库。它可以依赖 Spring AI API，但所有对象都通过普通构造器/Builder 创建；请求级 Publisher、execution 和 session lease 不进入共享 runtime 状态。

目标包：`api`、`builder`、`registry`、`definition`、`model.springai`、`tool`、`repository.memory`、`conversation`、`event`、`execution`、`skill`、`mcp`、`subagent`、`resource`。

## RUN-01 实现 ApexAgentRuntime Builder、注册表与定义 Provider

### 实现目标

提供外部项目只依赖 runtime 即可使用的公共 API，完成默认值、单一 DefinitionProvider、工具/Hook/Skill 注册表和 Java/YAML Provider 装配，但不提前执行动态定义构造。

### 涉及模块/类

目标：

- `runtime.api.ApexAgentRuntime`、`ApexAgentRuntimeBuilder`
- `registry.ToolRegistry`、`HookRegistry`、`SkillRegistry`
- `definition.ProgrammaticAgentDefinitionProvider`
- `FileAgentDefinitionProvider`
- `RuntimeDefaults`、`RuntimeConfiguration`

源参考：`GlobalToolRegistry`、`BuiltInToolProvider`、`AgentDefinitionClasspathYmlLoader`；不迁移其 Spring Bean 查找和全局/workspace 合并。

### 核心流程

Builder build：

```text
validate exactly one model entry
  -> resolve exactly one DefinitionProvider
  -> register kit defaults
  -> register caller tools/hooks/skills
  -> validate duplicate names/descriptors
  -> fill repositories/window/compaction/publisher/coordinator defaults
  -> initialize optional integration managers
  -> freeze registries/config/resources
  -> create ApexAgentRuntime
```

请求时 runtime 才将 registries/ports 组装为 core AgentPorts并调用 ApexAgentFactory。

### 接口和数据结构

```java
ApexAgentRuntime.builder()
    .chatModel(chatModel)                    // 或 modelGateway，二选一
    .agentDefinition(definition)             // convenience
    .agentDefinitionProvider(provider)       // 二选一
    .sessionRepository(repository)
    .conversationRepository(repository)
    .defaultEventPublisherFactory(factory)
    .sessionExecutionCoordinator(coordinator)
    .maxIterations(30)
    .hardContextLimit(...)
    .registerTool(tool)
    .registerHook("name", hook)
    .registerSkill(skill)
    .ownedResource(resource)
    .borrowedResource(resource)
    .build();
```

`.agentDefinition` 包装为 Programmatic Provider，不与 provider 同时使用。chatModel 包装为 SpringAiModelGateway，不与 modelGateway 同时使用。

### 关键实现逻辑

- Builder 只验证注册表自身：重复工具名、重复 Hook 注册名、Hook descriptor 自洽、Skill 名、资源所有权；不调用 Provider.load。
- 默认 AgentDefinition 是一个明确 Programmatic 对象，使用 runtime 内置 ReAct Prompt 文本；仓库当前不存在 `agents/default_agent/REACT_PROMPT.md`，不能依赖该缺失路径。
- File Provider 接收调用方显式的一个 YAML URI，文件包含完整 agents map。`classpath:` 用 ClassLoader，`file:` 用 JDK Files；UTF-8；初始化读取一次并把 prompt URI解析为文本后缓存，不扫描、不热加载。
- File Provider `listAgents()` 从缓存 metadata 直接返回，不调用 load 计数路径。
- registry 冻结后不可注册；请求共享只读实例。
- 静态预检为显式 `.prevalidateStaticDefinitions(true)`，调用 core 同一 validator；默认 false，请求时仍校验。

### 异常处理

- 多/零 Provider、多/零 model entry、重名、非法 descriptor 在 build 阶段抛 `RuntimeConfigurationException`，聚合全部错误。
- YAML 资源不存在、重复 agentKey、prompt 无法读取在 File Provider 初始化失败。
- 可选 MCP/SubAgent 初始化失败由 RUN-06/07 记录 unavailable，不让整个 build 失败。

### 测试方案

- 最小无工具 runtime 不启动 ApplicationContext并完成一次执行。
- 每个互斥 builder 选项、重名/非法 descriptor。
- 动态 Provider build load=0、请求时=1。
- File Provider classpath/file、一次缓存、文件修改不热加载、多 Agent metadata。
- 默认 build 不创建 MCP/SubAgent/外部 Skill资源。

### 架构符合性

runtime 只装配端口并调用 core 工厂，AGENT_BUILD 与定义校验仍在 core；公共 API 无 Spring IoC 前提。

## RUN-02 实现 Spring AI 中立模型与工具适配

### 实现目标

在 runtime 边界完成 common 与当前 Spring AI ChatModel/Message/ChatResponse/ToolDefinition 的双向转换，并保证模型只返回 ToolCall，由 core 统一执行工具。

### 涉及模块/类

源：`ModelResponseStreamer`、`SpringAiAgentToolExecutor`、`CustomToolCallingManager`、`MessageDeserializer`、`ChatResponseDeserializer`。

目标：`SpringAiModelGateway`、`SpringAiMessageMapper`、`SpringAiToolDefinitionAdapter`、`SpringAiStreamAccumulatorAdapter`、`SpringAiMetadataMapper`。

### 核心流程

1. ModelRequest -> Spring AI messages/options/tool definitions。
2. 显式关闭 Spring AI 自动工具执行，防止绕过 core PRE/POST Hook。
3. 订阅流，将正文/tool-call delta 转 ModelStreamChunk。
4. observer 取消/抛错时 dispose subscription。
5. 聚合最终 Spring AI response -> common ModelResponse。

### 接口和数据结构

Mapper 覆盖 System/User/Assistant/ToolResponse 角色；Assistant 工具调用保留 ID/name/arguments/ordinal。vendor metadata 只复制 Jackson 可序列化的白名单基础类型，未知不可序列化对象记录 key 并拒绝。

工具只适配定义给 ChatModel，不再通过 SpringAiAgentToolExecutor/ToolCallingManager 执行；真实 AgentTool.execute 由 CORE-06 直接调用。

### 关键实现逻辑

- 当前 POM 同时出现 Spring AI 1.1.2、2.0.0-M1 和 Alibaba RC 依赖。FND 基线先记录 resolved API；本任务必须选定一套与当前 ChatModel 实际兼容的 dependencyManagement，并通过 Enforcer convergence。不能让两个 ToolCall 模型并存。
- 自动工具执行开关在当前 Spring AI 版本用实际 ChatOptions API设置，并写集成测试证明 ToolCallback 未被框架调用。
- arguments 原始 JSON必须解析为对象 Map；保留原始字符串可放 metadata 供诊断，不作为执行输入。
- 流最终只有正文时用累计文本构造 response；只有 ToolCall 时也合法；二者都无则模型响应非法。
- Gateway 内部重试在 adapter 内使用同一个 request/observer，不能回调 core lifecycle。

### 异常处理

- subscription error/interrupt/empty response 包装 ModelInvocationException。
- observer 发布失败立即取消流并传播原异常。
- ToolCall 参数 JSON 非对象、重复 ID或缺 name 包装 InvalidModelResponseException。

### 测试方案

- 使用真实当前版本 Spring AI Message/ChatResponse 样本 round-trip。
- text、tool-only、text+multi-tool、ToolResponse history。
- 自动工具执行关闭的 spy ToolCallback测试。
- 流取消、内部重试、错误和空响应。
- core/common 无 Spring AI import，runtime dependency convergence。

### 架构符合性

所有 Spring AI 类型停留在 runtime adapter，core 只依赖中立模型，工具执行语义仍由 core 控制。

## RUN-03 实现内存存储、对话窗口与默认压缩能力

### 实现目标

提供 runtime 默认 Session/Conversation Repository、窗口、阈值策略和摘要 Compactor，支持同一 runtime 实例内连续对话和挂起恢复，并保证存储对象不发生别名污染。

### 涉及模块/类

源：`InMemorySessionContextStore`、`DefaultConversationMemoryManager`、`SimpleTokenEstimator`、`LlmDialogueSummaryGenerator`。

目标：`InMemorySessionRepository`、`InMemoryConversationRepository`、`DefaultConversationWindowManager`、`DefaultCompactionPolicy`、`DefaultConversationCompactor`、`TokenEstimator`。

### 核心流程

- Session save/load 双侧 JsonUtils.deepCopy。
- Conversation append 按 session 存有序 entry，检查 entryId/sortNo 幂等。
- Window load 摘要 + 未压缩消息 + retain recent。
- Policy 统计 message/system/tools 后判断。
- Compactor 分片调用独立 summary client，合并摘要结果。

### 接口和数据结构

内存结构建议：

```text
ConcurrentHashMap<String, SessionSnapshot> sessions
ConcurrentHashMap<String, ConversationState> conversations
ConversationState {
  NavigableMap<Long, AgentMessageEntry> entriesBySortNo
  Map<String, Long> sortNoByEntryId
  ConversationSummary summary
}
```

每个 ConversationState 内用锁保护单次 append/compact；跨 repository 不共享锁。

### 关键实现逻辑

- save 时 deepCopy 后存；load 再 deepCopy。不可变 record 仍需复制嵌套动态 Map。
- append 相同 entryId+相同内容视为成功；相同 ID内容不同或同 sortNo不同 ID抛冲突。
- compact 在单个 ConversationState 锁内一次更新 summary/compacted 标记/boundary，重复 compactionId 幂等。
- 默认估算可采用 `ceil(chars/4)` 加消息/工具结构开销，但 Check 必须分项记录；算法可替换。
- Compactor 的 summary client 是 runtime 内部独立对象，可复用底层 ChatModel但不复用 ApexAgent/ModelGateway lifecycle wrapper。
- 超长历史按 token/字符硬上限分片，任何一个分片都先验证。

### 异常处理

- duplicate conflict、sort gap 非法、compact boundary 不连续抛 RepositoryException。
- summary 模型异常向 core 传播；不产生半摘要。
- deepCopy 失败视为保存失败，不保留原对象引用作为降级。

### 测试方案

- save 后修改源、load 后修改返回值两个方向，覆盖嵌套对象。
- entry/compaction 幂等与冲突。
- window 摘要边界和最近消息保留。
- policy false/true，system/tool 占用触发。
- Compactor 分片硬限、失败不部分提交、内部模型不递归 gate。

### 架构符合性

会话连续性从 memory 中剥离为 runtime 默认实现，长期 Memory 不再是 core 必需依赖。

## RUN-04A 实现 ApexAgentExecution 状态机

### 实现目标

用请求级原子状态持有 core Agent、Once Publisher、lease 和终止回调，保证 run/cancel/close 竞争下 Agent 至多运行一次、资源至多收口一次。

### 涉及模块/类

目标：`runtime.execution.ApexAgentExecution`、`ExecutionState`、`ExecutionTerminator`。

### 核心流程

- 初态 PREPARED。
- run CAS PREPARED->RUNNING，调用 ApexAgent.run；finally terminateOnce。
- cancelBeforeStart CAS PREPARED->TERMINATED，END+release。
- close 若 PREPARED 则等价 cancel；RUNNING 不抢占执行，仅由 run finally终止；TERMINATED no-op。

### 接口和数据结构

`ExecutionTerminator` 持有 Runnable/接口回调：publishEnd、close request resources、release lease；用 AtomicBoolean 执行一次。Execution 不把底层 SseEmitter 暴露。

### 关键实现逻辑

- 第二次 run 抛 IllegalExecutionStateException，不静默并行。
- cancelBeforeStart 在 RUNNING/TERMINATED 返回 false，不重复 END。
- run outcome/异常对调用者可见；finally 收口异常作为 suppressed，不覆盖主异常。
- close 不等待长期运行，避免死锁；调用方取消运行中模型依赖 observer cancellation/上层线程中断策略，非本任务范围。

### 异常处理

- Agent.run 异常原样传播，terminator 必执行。
- END publish失败仍继续 release lease。
- lease release失败记录/传播为终止异常，但幂等状态保持 TERMINATED。

### 测试方案

- 多线程 run/run、run/cancel、cancel/close、close/close 竞态。
- 正常、Agent异常、END异常、release异常的调用次数和 suppressed。
- 未 run 的 try-with-resources 能释放。

### 架构符合性

执行所有权集中在请求级句柄，platform 只派发或取消，不复制状态。

## RUN-04B 实现 SessionExecutionCoordinator 与 lease

### 实现目标

实现立即失败、单进程的 session lease，使 NEW/HUMAN_RESPONSE 在 runtime API 返回前同步竞争同一 sessionId。

### 涉及模块/类

目标：`SessionExecutionCoordinator` interface、`SessionExecutionLease` interface、`InProcessSessionExecutionCoordinator`、`SessionBusyException`。

### 核心流程

acquire 用 `ConcurrentHashMap.compute(sessionId, ...)`：无 entry 新建含 owner token 的持有项；有 entry 立即抛 busy。release 也用 compute，在原子函数中校验 token 并删除。

### 接口和数据结构

```java
interface SessionExecutionCoordinator {
    SessionExecutionLease acquire(String sessionId);
}

interface SessionExecutionLease extends AutoCloseable {
    String sessionId();
    String ownerToken();
    boolean release();
}
```

接口位于 runtime，因为它是 runtime 公共 SPI，不被 core 使用；默认实现只在 runtime 实例内共享。

### 关键实现逻辑

- 不排队、不 blocking wait；已有执行立即 busy，满足 HTTP 409。
- release 必须校验同一 entry/token；旧 lease 不能删除新持有者。
- lease 内 AtomicBoolean 保证多次 release 只有第一次进入 map compute。
- runtime close 检查活动 lease；默认记录 warn，不强制破坏正在运行的 execution，关闭顺序先拒绝新请求再等待/取消由 RUN-08 定义。

### 异常处理

- blank sessionId 构造错误。
- token 不匹配是内部状态异常并记录 error，不删除 entry。
- acquire compute 内 busy 异常同步传播。

### 测试方案

- 同 session 第二次同步 busy，不同 session 并行。
- release 后重入、高并发 acquire/release、旧 lease 重复 release。
- 专测“旧 release 与新 acquire 竞态”不产生双锁。

### 架构符合性

runtime 成为唯一并发正确性来源；默认实现明确不宣称跨实例安全。

## RUN-04C 实现请求级 Once Publisher 并集成 execution/lease

### 实现目标

完成 runtime `newAgent/resumeAgent` 的同步准备：先 lease、再请求级 Publisher、再 core Factory，最后返回统一持有三者的 execution。

### 涉及模块/类

目标：`OnceAgentEventPublisher`、`PrintAgentEventPublisher`、`DefaultAgentEventPublisherFactory`、ApexAgentRuntime new/resume 方法、`ExecutionPreparation`。

### 核心流程

```text
acquire lease
  -> choose explicit publisher or factory.create
  -> wrap Once
  -> build request AgentPorts
  -> core factory createNew/createResumed
  -> return ApexAgentExecution
```

任一步失败按相反顺序 END best-effort、关闭请求 publisher（若可关闭）、release lease，再传播。

### 接口和数据结构

```java
ApexAgentExecution newAgent(AgentRequest request);
ApexAgentExecution newAgent(AgentRequest request, AgentEventPublisher publisher);
ApexAgentExecution resumeAgent(HumanResponseCommand command);
ApexAgentExecution resumeAgent(HumanResponseCommand command, AgentEventPublisher publisher);
```

Once 状态 OPEN/ENDED；首个 END CAS 后转发，后续 END no-op，END 后非 END 抛 EventStreamClosedException。

### 关键实现逻辑

- lease 必须最先获取，使同 session 的构造读取也串行。
- Factory 每次调用创建新 Print Publisher，Builder 不接受共享有状态 Publisher。
- ToolExecutionObserver 绑定本次 Once，不持有 runtime 默认 Publisher。
- core 构造失败 runtime 已发送 END后抛 PreparationException。platform 对非 busy 准备失败的兼容策略见 PLAT-02：返回已完成的 emitter，而不是把异常映射为新协议错误；该策略需风险 R-05 确认。
- Once 的 END delegate 失败也标记 ENDED，防止多路重复写坏连接。

### 异常处理

- SessionBusyException 在创建 Publisher/core 前同步抛，不发送 END。
- publisher factory/core factory失败执行 best-effort END/release，主异常保留。
- cancelBeforeStart 复用 execution terminator。

### 测试方案

- new/resume 返回前 lease 已持有；同 session 冲突。
- 显式/默认 Publisher每请求独立，连续 HUMAN_RESPONSE 不复用 END状态。
- core/构造失败/cancel/正常/挂起竞争下 END和release各一次。
- Tool observer 事件进当前 Publisher，禁止 END。

### 架构符合性

事件与 lease 都是请求级资源，runtime 统一所有权，platform 无需第二套锁或 END flag。

## RUN-05 迁移普通 Skill 加载、激活与资源读取

### 实现目标

保留当前非 learning 文件 Skill 的发现、解析、instructions、资源读取和 activate_skill 行为，同时把激活状态改为 session 隔离并移除固定 Prompt 注入。

### 涉及模块/类

源：`skills/definition`、`FileSystemSkillLoader`、`Skills`、`ActivateSkillToolCallback`、`ReadResourceToolCallback`；排除 `skills/learning`。

目标：`FileSkillProvider`、`SkillRegistry`、`ActivateSkillTool`、`ReadSkillResourceTool`、`SkillPathGuard`。

### 核心流程

- runtime 启动从显式 Skill root加载 registry。
- Assembler 校验 definition.enabledSkills 存在。
- activate_skill 校验 enabled，读取 instructions，返回 ToolResult并由 core 更新 activatedSkills。
- read resource 校验 enabled + path，返回内容。

### 接口和数据结构

`SkillDefinition` 保存 name/description/instructions/资源 descriptors；大型资源不全量进定义快照，只保存稳定 resource name/relative path/hash，实际读取由 Provider。

激活工具重复调用仍返回 instructions；activated set add 是幂等。instructions 只作为 ToolResult entry进入 Conversation。

### 关键实现逻辑

- 文件来源由 Builder 显式指定，不扫描用户机器默认目录。
- 所有路径 normalize 后必须仍在 Skill root；拒绝 absolute、`..` 穿越和符号链接逃逸。
- 不同 session 状态只在 SessionSnapshot；registry/SkillDefinition 可共享只读。
- 新 Turn definition 移除 Skill 时 core清理 activated并 warn；runtime 不全局删 registry。
- learning 包和两个 learning Hook 不注册、不打进 runtime artifact。

### 异常处理

- Skill 不 enabled/不存在/资源越界转模型可见 ToolResult，不泄漏绝对路径。
- Skill 解析失败在 Provider 初始化时明确失败；若作为可选外部资源，可单项 unavailable 并从定义剔除，但需与工具健康策略一致。
- instructions 读取编码固定 UTF-8。

### 测试方案

- 现有非 learning 行为回归。
- activated 跨 Turn、跨 session/user隔离、重复激活。
- instructions 只出现于 ToolResult，不固定注入。
- path traversal/symlink/不存在资源。
- runtime artifact 无 `skills.learning`。

### 架构符合性

普通 Skill 是 runtime 可选工具能力，session 状态由 core；Skill Learning 独立封存 memory。

## RUN-06 迁移 MCP stdio/SSE 集成

### 实现目标

将 MCP server 工具适配为 AgentTool，支持 stdio/SSE transport，由 runtime 管理客户端、进程、连接、超时和关闭；调用载荷只含最终工具参数。

### 涉及模块/类

源：`definition/mcp/*`、`GlobalToolRegistry` MCP 部分、Spring AI MCP starter适配。

目标：`McpRuntimeManager`、`McpTransport`、`StdioMcpTransport`、`SseMcpTransport`、`McpClientHandle`、`McpAgentToolAdapter`、`McpServerDefinition`。

### 核心流程

1. Builder 接收显式 server definitions。
2. 每 server 创建 transport/client，initialize/list tools。
3. 工具名按稳定 server 前缀注册，生成 AgentTool adapters。
4. execute 只序列化 call.arguments。
5. runtime close 按 tool adapters -> clients -> transport/process 顺序关闭。

### 接口和数据结构

`McpTransport` 是 runtime 内接口，包含 connect/listTools/call/close；不进入 core-extension。ServerDefinition 指定 id、transport type、command/args 或 endpoint、timeouts、环境变量白名单。

`McpAgentToolAdapter.execute` 忽略 ToolExecutionContext业务字段，只使用 ToolCall.arguments；observer 默认不发布事件。

### 关键实现逻辑

- 未配置 server 时 manager 不创建 executor/client/process。
- client cache key 包含 runtime instance + server definition fingerprint，不使用 static cache。
- stdio 用 ProcessBuilder 参数列表，不拼接 shell字符串；只传显式环境变量。
- SSE endpoint 校验 URI scheme，连接/重连有上限和退避，close 取消调度。
- 初始化失败关闭该 server 已创建资源，记录 unavailable tool pattern；通过 ToolAvailabilityProvider 让 Assembler/Session 特殊清理，健康 server 继续。
- 失败工具不能进入模型列表；已挂起该工具恢复时由 core生成不可用 ToolResult。

### 异常处理

- init失败 warn并降级，无 fail-fast开关。
- 单次调用 timeout/协议错误抛 ToolExecutionException，由 core转 ToolResult。
- close聚合异常并继续关闭其他资源。
- 日志不输出完整工具参数、环境变量或用户上下文。

### 测试方案

- Fake stdio process/SSE server 的 initialize/list/call/timeout/close。
- 参数泄漏测试：请求 JSON只有工具 arguments。
- cache隔离、未配置零资源、runtime close。
- 一个 server init失败、另一个正常，定义/session剔除与健康工具可用。
- 命令参数含空格不经 shell解释。

### 架构符合性

MCP 完全停留 runtime，core 只看 AgentTool；资源生命周期由 runtime AutoCloseable 管理。

## RUN-07 迁移 HTTP SubAgent 工具

### 实现目标

把远程普通 Agent 通过现有 chat/SSE 协议适配为 AgentTool，使用独立子 session，聚合正文、透传允许事件，并防止递归闭环。

### 涉及模块/类

源：`SubAgentToolCallback`、`SubAgentToolCallbackProvider`、`tool/handler/*`、Fastjson SSE 解析。

目标：`HttpSubAgentTool`、`SubAgentHttpClient`、`SseEventDecoder`、`SubAgentEventAggregator`、`SubAgentCallTrace`、`SubAgentRuntimeManager`。

### 核心流程

1. 校验 trace 深度和 agentKey未重复。
2. IdGenerator 创建独立 child sessionId。
3. POST ChatRequest NEW，Header X-User-Id。
4. 逐 SSE data 解析 protocol AgentMessage。
5. STREAM_CONTENT 按 content_id/到达顺序聚合；INVOCATION 经 observer；ARTIFACT忽略；END完成工具。
6. 返回聚合正文 ToolResult。

### 接口和数据结构

`SubAgentCallTrace` 是 common 中立 record：`String traceId, List<String> agentKeys, int maxDepth`，作为 ToolExecutionContext 的明确字段传递，不进入前端协议。每次子调用创建新不可变对象并追加目标 agentKey。

SubAgentDefinition 含 targetAgentKey、endpoint、description、timeout；工具名由定义显式指定或稳定 `subagent/{key}`。

### 关键实现逻辑

- 推荐使用 JDK HttpClient，避免 runtime 引入完整 Spring Web；HTTP client可外部借用或 runtime创建。
- SSE decoder处理 `data:` 多行、空行事件边界、UTF-8、CRLF/LF和注释行；每个 data JSON交 protocol mapper。
- 远端 END只结束子工具，不调用 observer。
- 远端 ASK_HUMAN/TOOL_CONFIRMATION 无法由父请求安全恢复子 session；首版明确中止子调用并返回“子智能体请求人工介入，当前工具调用不支持透传恢复”的 ToolResult。不能把交互事件透传给父前端造成错误 session 关联；该限制列入风险 R-12。
- INVOCATION only经 core observer allowlist；STREAM_CONTENT不透传而聚合。
- init失败关闭 client资源、标 unavailable并继续其他工具；调用期超时转换 ToolResult。

### 异常处理

- HTTP非2xx、SSE malformed、无 END、timeout/cancel都抛 ToolExecutionException。
- 远端未知 event_type 记录类型并失败，不静默丢关键语义。
- recursion/depth 在发 HTTP 前拒绝。
- 日志不输出 X-User-Id或正文。

### 测试方案

- 独立 child session、agentKey、X-User-Id、NEW payload。
- SSE多行/分片、content聚合、invocation observer、artifact ignore、remote END不结束父请求。
- ASK_HUMAN/CONFIRMATION显式转失败结果。
- depth/agent闭环、timeout/cancel、malformed JSON。
- init失败单项降级、Fastjson源码/依赖清零。

### 架构符合性

SubAgent 仍是普通工具，协议复用 protocol，core 不感知父子关系；防递归信息只在 runtime 内部调用链。

## RUN-08 完成资源生命周期、runtime-only 示例与集成验收

### 实现目标

收口 runtime 自有/借用资源的所有权、关闭顺序和示例，证明无 Spring IoC 下执行、压缩、挂起恢复和可选集成降级均可运行。

### 涉及模块/类

`ApexAgentRuntime.close`、`ResourceRegistry`、RUN-01～07 全部默认实现、`runtime/src/test` integration、`runtime/examples` 或测试资源示例。

### 核心流程

runtime close：拒绝新 execution -> 等待配置的短 grace period -> 取消/关闭自有 execution资源 -> SubAgent -> MCP -> summary clients -> executors/schedulers -> repositories（若 AutoCloseable）。借用资源不关闭。

### 接口和数据结构

Builder 每个可关闭依赖必须显式 `ownedXxx` 或 `borrowedXxx`，默认调用者注入对象按 borrowed 处理，runtime 内创建按 owned。ResourceRegistry 按反向注册顺序关闭并聚合错误。

示例覆盖：最小执行、显式 Publisher、ask_human 挂起/恢复、自定义 Tool/Hook、close。

### 关键实现逻辑

- close 用 AtomicBoolean 幂等；close 后 new/resume 抛 RuntimeClosedException。
- 未配置能力的 lazy supplier 不求值，线程/客户端为 0。
- 活动 execution 的处理策略必须在 Builder契约明确；推荐 grace period 后触发 observer cancellation，但不能释放仍运行代码持有的 lease，最终 release仍由 execution。
- integration初始化失败产生 unavailable snapshot，验证三层工具清理后运行继续。
- artifact dependency tree 只含 core/kit及最小 Spring AI/JSON/HTTP需要，不含 platform/memory/Spring Boot starter。

### 异常处理

- close各资源失败聚合为 `RuntimeCloseException`，全部资源仍尝试。
- 示例不依赖真实外部 MCP/Skill路径或网络。
- runtime-only模型用 Fake/本地 deterministic gateway。

### 测试方案

- 无 ApplicationContext 的 NEW、工具、两 Iteration、压缩、QUESTION恢复。
- Print Publisher protocol Golden File。
- close幂等、owned关闭、borrowed不关闭、关闭顺序、零可选资源。
- MCP/SubAgent部分 init失败仍完整执行。
- dependency tree与线程/进程泄漏检查。

### 架构符合性

runtime 成为真正可嵌入的开箱即用层，所有平台能力仍在 platform，memory不进入依赖图。
