# runtime 模块详细设计

## 模块设计定位

`apex-agent-runtime` 把 core、kit 和默认端口实现装配成无需 Spring IoC 的可运行库。它可以依赖 Spring AI API，但所有对象都通过普通构造器/Builder 创建；请求级 Publisher、execution 和 session lease 不进入共享 runtime 状态。

runtime POM 直接声明 protocol、common、core-extension、core、kit；这些模块的类型均被 runtime 源码直接使用，不能依靠 core/kit 的传递依赖。

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
- 可选 MCP/SubAgent 初始化失败由 RUN-06/07 记录 unavailable，不让整个 runtime build 失败；但受影响 Agent 的新活动绑定会在 core 构造时失败，不能被 Builder 静默删掉。

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

1. 读取 FND-01 declared/resolved/API 基线，先移除可由既有 BOM 管理的叶子覆盖，并尝试从仓库已经声明的版本线形成单一候选集合。
2. 对候选运行 Enforcer convergence、runtime 编译和真实类型 smoke/round-trip 测试；失败证据按 artifact、异常和 API 签名归档。
3. 只有 Q-17 触发条件成立时，按“最少 BOM/版本属性变化”生成下一候选；禁止在子模块逐个 pin 叶子 artifact。
4. 选定候选后实现 ModelRequest -> Spring AI messages/options/tool definitions。
5. 显式关闭 Spring AI 自动工具执行，防止绕过 core PRE/POST Hook。
6. 订阅流，将正文/tool-call delta 转 ModelStreamChunk。
7. 订阅建立后立即通过 `observer.cancellationToken().onCancel(subscription::dispose)` 注册主动取消动作；observer 抛错时也发出同一 token 的取消命令。
8. 聚合最终 Spring AI response -> common ModelResponse，并完成 legacy 基线与对齐报告后删除 R-13 临时豁免。

### 接口和数据结构

Mapper 覆盖 System/User/Assistant/ToolResponse 角色；Assistant 工具调用保留 ID/name/arguments/ordinal。vendor metadata 只复制 Jackson 可序列化的白名单基础类型，未知不可序列化对象记录 key 并拒绝。

工具只适配定义给 ChatModel，不再通过 SpringAiAgentToolExecutor/ToolCallingManager 执行；真实 AgentTool.execute 由 CORE-06 直接调用。

`SpringAiDependencyAlignmentReport` 是实施证据，不进入运行 API，至少包含：`coordinate`、`declaredVersions`、`resolvedBefore`、`selectedVersion`、`changeType`、`triggerEvidence`、`apiEvidence`、`tests`。报告必须明确 Spring Boot 与模型供应商 SDK before/after 相同。

### 关键实现逻辑

- 当前 POM 同时出现 Spring AI 1.1.2、2.0.0-M1 和 Alibaba RC 依赖。FND 基线先记录 resolved API；本任务必须选定一套与当前 ChatModel 实际兼容的 dependencyManagement，并通过 Enforcer convergence。不能让两个 ToolCall 模型并存。
- 候选优先级固定为：删除显式叶子覆盖并服从已有 BOM；从仓库已声明版本中选单一版本线；最后才引入最少的新 Spring AI BOM/属性值。优先级较低的方案必须附上较高方案失败证据。
- 版本所有权只在父 POM/BOM。Spring Boot、模型供应商 SDK 不在本次调整白名单；若其版本成为唯一阻塞，记录依赖对齐阻塞并重新提请决策，不得静默扩围。
- 自动工具执行开关在当前 Spring AI 版本用实际 ChatOptions API设置，并写集成测试证明 ToolCallback 未被框架调用。
- arguments 原始 JSON必须解析为对象 Map；保留原始字符串可放 metadata 供诊断，不作为执行输入。
- 流最终只有正文时用累计文本构造 response；只有 ToolCall 时也合法；二者都无则模型响应非法。
- Gateway 内部重试在 adapter 内使用同一个 request/observer，不能回调 core lifecycle。
- 每次重试开始前检查 token；活动 subscription 的 registration 在该次订阅结束时注销，避免取消命令作用到已结束对象。

### 异常处理

- subscription error/interrupt/empty response 包装 ModelInvocationException；token 已取消导致的终止统一转换为 `CancellationRequestedException`，由 core 记录 `CANCELLED`。
- observer 发布失败立即取消流并传播原异常。
- ToolCall 参数 JSON 非对象、重复 ID或缺 name 包装 InvalidModelResponseException。
- 所有候选均无法在允许边界内同时通过 convergence、编译和契约测试时，保留失败报告并阻塞 RUN-02；不得选择“编译通过但契约失败”或保留多个大版本共存。

### 测试方案

- 使用真实当前版本 Spring AI Message/ChatResponse 样本 round-trip。
- text、tool-only、text+multi-tool、ToolResponse history。
- 自动工具执行关闭的 spy ToolCallback测试。
- token 在订阅建立前/后取消、内部重试、错误和空响应；两种时序都断言活动 subscription 的 dispose 被调用。
- core/common 无 Spring AI import，runtime dependency convergence。
- 对齐前后 dependency tree 比较；R-13 豁免清零；报告中的每个版本变化都有对应触发证据。
- legacy 基线、runtime 全套 Adapter 测试同时通过；Spring Boot 与模型供应商 SDK resolved 版本未变化。

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

目标：`runtime.execution.ApexAgentExecution`、`ExecutionState`、`ExecutionTerminator`、`RuntimeCancellationSource`、`ActiveExecutionRegistry`。

### 核心流程

- 初态 PREPARED；execution 构造完成后先登记到 runtime 活动表，再返回调用方。
- `run()` CAS PREPARED->RUNNING，调用 ApexAgent.run；finally 调用 `terminateOnce`。
- `cancel()` 在 PREPARED 时先 CAS 为 CANCEL_REQUESTED，使并发 `run()` 不能启动，再发出 token 命令、调用 `ApexAgent.cancelBeforeRun()` 保存 CANCELLED 并立即收口；在 RUNNING 时 CAS 为 CANCEL_REQUESTED、发出 token 命令后立即返回，实际执行仍由 run finally 收口。
- `cancelBeforeStart()` 只接受 PREPARED，供线程池拒绝路径表达明确意图；其终止效果与 PREPARED 状态的 `cancel()` 一致。
- `close()` 委托 `cancel()`，因此 RUNNING 也会收到取消命令；TERMINATED no-op。

### 接口和数据结构

```java
enum ExecutionState { PREPARED, RUNNING, CANCEL_REQUESTED, TERMINATED }

final class ApexAgentExecution implements AutoCloseable {
    AgentRunOutcome run();
    boolean cancel();
    boolean cancelBeforeStart();
    @Override void close();
}
```

`RuntimeCancellationSource` 内部持有原子 cancelled 标志和线程安全 callback 集合，对 core/adapter 只暴露 common `CancellationToken`。`ExecutionTerminator` 持有 publishEnd、close request resources、release lease、unregister execution 回调，并用 AtomicBoolean 执行一次。Execution 不把底层 SseEmitter 暴露。

### 关键实现逻辑

- 第二次 run 抛 IllegalExecutionStateException，不静默并行。
- 首次 `cancel()` 负责发出取消命令；并发或重复 cancel 返回 false，不重复执行 callback。`cancelBeforeStart()` 在 RUNNING/CANCEL_REQUESTED/TERMINATED 返回 false。
- `cancel()` 返回 true 只表示命令首次发出。若 core 已在相邻竞态中提交自然终态，命令不回滚或重写该终态；core 在终态前观察到 token 时才保存 CANCELLED。测试允许该线性化边界的两种合法结果，但 END/release/callback 次数必须唯一。
- run outcome/异常对调用者可见；finally 收口异常作为 suppressed，不覆盖主异常。
- 取消是发命令而非等待协议：RUNNING 状态不在 cancel/close 线程中发布 END、关闭请求资源或释放 lease，避免仍运行代码使用已释放资源。
- `terminateOnce` 固定顺序为发布 END、关闭请求资源、释放 lease、从活动表注销；前一步失败不阻断后一步。
- `terminateOnce` 在调用上述外部回调前先 CAS 到 TERMINATED；否则 END/complete 触发的 emitter completion callback 会在 RUNNING 状态反向调用 cancel，污染正常 outcome。
- runtime 不设置取消超时或 grace period，也不把线程中断当作统一语义；默认适配器必须注册底层取消动作，自定义不合作工具可能长期占用 execution/lease，这是公开限制。

### 异常处理

- Agent.run 异常原样传播，terminator 必执行。
- PREPARED 取消保存失败仍执行 terminator；保存异常为主异常，END/close/release 失败按 suppressed 或日志规则附加。
- END publish失败仍继续 release lease。
- lease release失败记录/传播为终止异常，但幂等状态保持 TERMINATED。

### 测试方案

- 多线程 run/run、run/cancel、cancel/close、close/close 竞态；RUNNING 的 cancel 立即返回且底层 callback 精确执行一次。
- terminate 先置 TERMINATED 再 END/complete；模拟 completion callback 回调 cancel，断言返回 false、token 未被正常收口误取消。
- 正常、Agent异常、END异常、release异常的调用次数和 suppressed。
- 未 run 的 try-with-resources 会把 Session/Turn 记为 CANCELLED 并释放；RUNNING close 不提前释放 lease，run finally 才 END/release/unregister。

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
- runtime close 通过 RUN-04A 的活动 execution 注册表发出取消命令；协调器不直接释放活动 lease，实际 release 仍只由 execution terminator 完成。

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
  -> create request RuntimeCancellationSource
  -> build request AgentPorts with source.token
  -> core factory createNew/createResumed
  -> create and register ApexAgentExecution
  -> return ApexAgentExecution
```

lease 冲突在 Publisher 创建前直接传播。Publisher 已创建且 core factory 同步构造/恢复准备失败时，runtime 通过 Once 发布唯一 END、完成/关闭请求 publisher、release lease，再抛 `AgentPreparationException(endPublished=true)`；同步准备阶段不得先发布其他事件，因此 platform 可保证返回仅 END SSE。Publisher 自身创建失败因没有可用出口，释放 lease 后按基础设施异常传播，不伪称已发送 END。

### 接口和数据结构

```java
ApexAgentExecution newAgent(AgentRequest request);
ApexAgentExecution newAgent(AgentRequest request, AgentEventPublisher publisher);
ApexAgentExecution resumeAgent(HumanResponseCommand command);
ApexAgentExecution resumeAgent(HumanResponseCommand command, AgentEventPublisher publisher);
```

Once 状态 OPEN/ENDED；首个 END CAS 后转发，后续 END no-op，END 后非 END 抛 EventStreamClosedException。

`AgentPreparationException` 至少携带 `sessionId`、`agentKey`、`phase`、`endPublished` 和 cause；不得携带用户 query、工具参数或将异常文本写入 SSE。platform 只对 `endPublished=true` 使用“返回已完成 emitter”分支。

### 关键实现逻辑

- lease 必须最先获取，使同 session 的构造读取也串行。
- Factory 每次调用创建新 Print Publisher，Builder 不接受共享有状态 Publisher。
- ToolExecutionObserver 绑定本次 Once，不持有 runtime 默认 Publisher。
- ModelStreamObserver、ToolExecutionObserver、ToolExecutionContext 必须引用该 execution 的同一 token；Publisher 失败也通过该 source 发出取消命令。
- core 构造/恢复准备失败时 runtime 只能发送 protocol 既有的精确 END，随后抛 `AgentPreparationException(endPublished=true)`。platform 必须返回已完成 emitter，不得映射为新协议错误、HTTP 4xx/5xx 或追加 STREAM_CONTENT。
- Once 的 END delegate 失败也标记 ENDED，防止多路重复写坏连接。

### 异常处理

- SessionBusyException 在创建 Publisher/core 前同步抛，不发送 END。
- publisher factory 失败只 release 并传播；core factory 失败执行 best-effort END、publisher complete/close、release，主异常作为 `AgentPreparationException` cause 保留。
- cancel/cancelBeforeStart/close 复用 execution cancellation source 与 terminator；只有执行真正退出或从未启动时才运行 terminator。

### 测试方案

- new/resume 返回前 lease 已持有；同 session 冲突。
- 显式/默认 Publisher每请求独立，连续 HUMAN_RESPONSE 不复用 END状态。
- core 构造/恢复准备失败断言事件列表精确等于 `[END]`、`endPublished=true`、release一次；publisher factory失败断言无虚假 END标志。cancel/正常/挂起竞争下 END和release各一次。
- execution 返回前已登记；准备失败不残留登记。Publisher 失败会触发 token；执行 finally 后活动表清零。
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
- Skill 解析失败在 Provider 初始化时明确失败；本期不可借用 MCP/SubAgent 的不可用绑定例外将 Skill 从定义中静默剔除。
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

`McpAgentToolAdapter.execute` 的业务请求只使用 ToolCall.arguments，不把 ToolExecutionContext 隐式字段发送到 MCP；但必须使用 `context.cancellationToken()` 注册当前 MCP call handle 的 cancel 动作。observer 默认不发布事件。

### 关键实现逻辑

- 未配置 server 时 manager 不创建 executor/client/process。
- client cache key 包含 runtime instance + server definition fingerprint，不使用 static cache。
- stdio 用 ProcessBuilder 参数列表，不拼接 shell字符串；只传显式环境变量。
- SSE endpoint 校验 URI scheme，连接/重连有上限和退避，close 取消调度。
- 每次 call 创建独立可取消 handle；发送前检查 token，创建后立即注册 `handle.cancel()`，调用结束注销 registration。取消时停止重连/读取并抛 `CancellationRequestedException`，不能转换成普通“工具不可用” ToolResult。
- 初始化失败关闭该 server 已创建资源，按 `(MCP, serverId, stableNamePrefix)` 和已知精确工具名原子更新 ToolAvailabilitySnapshot，健康 server 继续。
- runtime 不直接改 AgentDefinition 或 SessionSnapshot。core 对新绑定抛 `UnavailableToolBindingException`；对已有 session 的旧绑定生成 `HistoricalToolBinding` 并退出有效集合。历史消息和旧定义投影保留，只读且不可执行。
- 失败工具不能进入模型列表；已挂起该工具恢复时由 core生成不可用 ToolResult。

### 异常处理

- init失败 warn并降级，无 fail-fast开关。
- 单次调用 timeout/协议错误抛 ToolExecutionException，由 core转 ToolResult；请求 token 取消使用 `CancellationRequestedException`，由 core 终止执行。
- close聚合异常并继续关闭其他资源。
- 日志不输出完整工具参数、环境变量或用户上下文。

### 测试方案

- Fake stdio process/SSE server 的 initialize/list/call/timeout/close；活动 call 取消时 handle.cancel 精确一次且不再重连。
- 参数泄漏测试：请求 JSON只有工具 arguments。
- cache隔离、未配置零资源、runtime close。
- 一个 server init失败、另一个正常：新绑定拒绝；旧绑定历史记录幂等、有效集合清理、历史消息不变；健康工具可用。
- 命令参数含空格不经 shell解释。

### 架构符合性

MCP 完全停留 runtime，core 只看 AgentTool；资源生命周期由 runtime AutoCloseable 管理。

## RUN-07 迁移 HTTP SubAgent 工具

### 实现目标

把能在单次 NEW 内自行完成的远程普通 Agent 通过现有 chat/SSE 协议适配为 AgentTool，使用独立子 session，聚合正文、透传允许事件，并防止递归闭环；本期不支持子 Agent 人工介入。

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
- 已确认：远端 ASK_HUMAN/TOOL_CONFIRMATION 不进入父 observer。收到后先停止解析与 INVOCATION 转发，主动取消/关闭子 HTTP 请求，再抛带稳定原因的 `ToolExecutionException`；父 core 按普通工具失败生成模型可见“子智能体请求人工介入，当前工具调用不支持透传恢复”结果。不得创建父子 session 映射或把子交互透传给父前端。
- INVOCATION only经 core observer allowlist；STREAM_CONTENT不透传而聚合。
- init失败关闭 client资源，按 `(SUB_AGENT, sourceId, exactToolName)` 更新 availability 并继续其他工具；runtime 不修改定义/session。新绑定由 core拒绝，旧绑定由 core迁移历史；调用期超时转换 ToolResult。
- HTTP 请求创建后立即把 `CompletableFuture.cancel(true)` 与 response body/stream close 注册到请求 token；任一阶段收到命令都停止读取，不再转发 INVOCATION，并抛 `CancellationRequestedException`。

### 异常处理

- HTTP非2xx、SSE malformed、无 END、业务 timeout 抛 ToolExecutionException；请求取消单独抛 `CancellationRequestedException`，不得由 adapter 伪装成普通失败 ToolResult。core 可按取消状态机为已追加 Assistant entry 的未完成调用统一补标准取消结果。
- 远端未知 event_type 记录类型并失败，不静默丢关键语义。
- recursion/depth 在发 HTTP 前拒绝。
- 日志不输出 X-User-Id或正文。

### 测试方案

- 独立 child session、agentKey、X-User-Id、NEW payload。
- SSE多行/分片、content聚合、invocation observer、artifact ignore、remote END不结束父请求。
- ASK_HUMAN/CONFIRMATION 触发子请求 cancel/close、停止后续事件读取，并显式转父工具失败结果；父 Publisher 交互事件次数为 0。
- depth/agent闭环、timeout、malformed JSON；token 在发请求前、等待响应和读取 body 三个阶段取消均主动 cancel/close 且停止事件转发。
- init失败覆盖新绑定拒绝、旧绑定只读留痕/不可执行、健康 SubAgent 可用，以及 Fastjson源码/依赖清零。

### 架构符合性

SubAgent 仍是普通工具，协议复用 protocol，core 不感知父子关系；防递归信息只在 runtime 内部调用链。

## RUN-08 完成资源生命周期、runtime-only 示例与集成验收

### 实现目标

收口 runtime 自有/借用资源的所有权、关闭顺序和示例，证明无 Spring IoC 下执行、压缩、挂起恢复和可选集成降级均可运行。

### 涉及模块/类

`ApexAgentRuntime.close`、`ActiveExecutionRegistry`、`ResourceRegistry`、RUN-01～07 全部默认实现、`runtime/src/test` integration、`runtime/examples` 或测试资源示例。

### 核心流程

runtime close：原子拒绝新 execution -> 快照活动 execution 并逐个调用 `cancel()` -> 若活动表为空则立即关闭自有共享资源；否则由最后一个 execution 注销时触发共享资源关闭。`close()` 在取消命令全部发出后返回，不等待 execution 结束，也不设置取消超时/grace period。借用资源不关闭。

### 接口和数据结构

Builder 每个可关闭依赖必须显式 `ownedXxx` 或 `borrowedXxx`，默认调用者注入对象按 borrowed 处理，runtime 内创建按 owned。`ActiveExecutionRegistry` 提供 register/unregister/snapshot，并与 accepting/closeRequested 原子协调，避免 close 与新 execution 登记竞态。ResourceRegistry 按反向注册顺序关闭并聚合错误，关闭顺序为 SubAgent -> MCP -> summary clients -> executors/schedulers -> repositories（若 AutoCloseable）。若 `close()` 时没有活动 execution，资源关闭同步执行且可向调用方抛 `RuntimeCloseException`；若需等最后一个 execution 注销后延迟关闭，调用方已返回，关闭异常只能按 runtimeId/resourceType 记录 error，不回抛到 execution 业务异常。

示例覆盖：最小执行、显式 Publisher、ask_human 挂起/恢复、自定义 Tool/Hook、close。

### 关键实现逻辑

- close 用 AtomicBoolean 幂等；close 后 new/resume 抛 RuntimeClosedException。
- 未配置能力的 lazy supplier 不求值，线程/客户端为 0。
- close 对每个活动 execution 只发一次请求级取消命令，不等待、不提前释放 lease。默认模型、MCP、HTTP SubAgent adapter 必须主动取消底层调用；不合作的自定义工具可能使共享资源关闭和 lease 释放无限延后。
- close 与 new/resume 通过活动表的同一原子门协调：关闭标志设置后不得登记新 execution；已经完成同步准备但登记失败的请求立即按 PREPARED cancel 收口。
- integration初始化失败产生 unavailable snapshot；验证受影响新绑定失败、旧绑定转历史且从有效三层工具状态移除，健康 Agent 运行继续。恢复健康后旧 session 不自动回填 enabledTools。
- artifact 直接项目依赖为 protocol、common、core-extension、core、kit，并只引入必要的 Spring AI/JSON/HTTP 外部库；不含 platform/memory/Spring Boot starter，dependency analyze 无 used-but-undeclared。

### 异常处理

- 发出取消命令时的 callback 异常按 executionId/callbackType 记录 error，但继续通知同 token 的其他 callback 和其他 execution；`cancel()` 仍返回“是否首次发命令”的 boolean，`close()` 不因等待执行终止而阻塞。
- runtime close 调用某个 PREPARED execution 的 `cancel()` 若因 `cancelBeforeRun()` 保存失败而抛错，必须记录后继续处理活动表其余项；全部取消命令尝试完成后把这类同步错误聚合进 `RuntimeCloseException`。活动 RUNNING execution 尚未退出本身不是 close 错误。
- 无活动 execution 的同步资源关闭失败聚合抛 `RuntimeCloseException`；延迟关闭发生在最后一个 execution 注销线程，失败聚合后记录 error，不能覆盖该 execution 的主异常，且全部资源仍尝试。
- 示例不依赖真实外部 MCP/Skill路径或网络。
- runtime-only模型用 Fake/本地 deterministic gateway。

### 测试方案

- 无 ApplicationContext 的 NEW、工具、两 Iteration、压缩、QUESTION恢复。
- Print Publisher protocol Golden File。
- close幂等、owned关闭、borrowed不关闭、关闭顺序、零可选资源。
- 活动模型/MCP/HTTP 执行中 close：取消命令各一次、close 立即返回、lease 不提前释放；执行 finally 后注销并由最后一个注销触发共享资源关闭。
- close 与 execution 登记竞态下不存在关闭后漏登记；不合作 Fake 工具证明 close 仍返回且其 lease 保持占用，明确无超时保证。
- 多个 PREPARED execution 中一个取消保存失败时，其他 execution 仍收到取消；同步错误在命令遍历后聚合，lease/活动表无可收口项泄漏。
- MCP/SubAgent部分 init失败仍完整执行。
- dependency tree与线程/进程泄漏检查。

### 架构符合性

runtime 成为真正可嵌入的开箱即用层，所有平台能力仍在 platform，memory不进入依赖图。
