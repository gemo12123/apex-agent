# core 模块详细设计

## 模块设计定位

`apex-agent-core` 只实现 Agent 语义：定义构造、Session/Turn/Iteration、11 个生命周期、唯一 ReAct 循环、工具处理、压缩门、挂起和恢复。它直接依赖 protocol、common 与 core-extension；protocol 用于事件 DTO，所有外部能力测试使用 Fake 端口。

目标包结构：

```text
org.gemo.apex.core
├── agent
│   ├── ApexAgent
│   ├── ApexAgentContext
│   ├── ApexAgentFactory
│   └── AgentPorts
├── definition
├── lifecycle
├── execution
├── model
├── tool
├── conversation
├── intervention
├── event
└── exception
```

## CORE-01 实现 AgentDefinitionAssembler 与 ApexAgentFactory

### 实现目标

将“加载定义 -> AGENT_BUILD -> 不可用绑定判定 -> 权威校验 -> 冻结”收敛到唯一 Assembler，并由 Factory 明确分开 NEW 与 HUMAN_RESPONSE。runtime 只能提供端口和调用入口，不能预先执行构造语义。任何生命周期中只有 AGENT_BUILD 可以修改定义。

### 涉及模块/类

源：`SuperAgentFactory`、`SuperAgentSessionService.prepareRuntimeContext`、`AgentDefinitionClasspathYmlLoader` 的部分校验/合并逻辑。

目标：

- `core.definition.AgentDefinitionAssembler`
- `AgentDefinitionValidator`
- `AgentDefinitionOperationApplier`
- `AgentDefinitionSnapshotFactory`
- `agent.ApexAgentFactory`
- `agent.AgentPorts`

### 核心流程

NEW 构造采用两阶段校验：

```text
Factory.load optional SessionSnapshot
  -> Provider.load(agentKey)
  -> structural precheck（版本、metadata、AGENT_BUILD Binding 可解析）
  -> create AgentDefinitionDraft
  -> snapshot and dispatch AGENT_BUILD bindings
  -> materialize candidate and ToolProvider.loadTools(candidate)
  -> refresh availability and classify new versus existing unavailable bindings
  -> reject new binding / migrate existing binding to history
  -> full validate
  -> freeze runtime snapshot + recovery projection
```

Factory 先校验已加载 session 的用户、agentKey 和 NEW 状态，再把它传给 Assembler 区分“新绑定”与“既有绑定”；Assembler 成功后才创建/更新 Turn 和 SessionSnapshot。恢复路径直接读取 `activeDefinition` recovery snapshot，验证 1.0.0、解析 Hook/Tool，不调用 Provider 或 AGENT_BUILD。

### 接口和数据结构

```java
final class AgentDefinitionAssembler {
    AgentAssemblyResult assemble(
            String agentKey,
            Optional<SessionSnapshot> existingSession,
            AgentPorts ports);
    ValidationReport validate(AgentDefinitionDraft draft, AgentPorts ports);
}

record AgentAssemblyResult(
        AgentDefinitionSnapshot definition,
        Set<String> effectiveEnabledTools,
        List<HistoricalToolBinding> historicalToolBindings) {}

final class ApexAgentFactory {
    ApexAgent createNew(AgentRequest request, AgentPorts ports);
    ApexAgent createResumed(HumanResponseCommand command, AgentPorts ports);
}
```

`AgentAssemblyResult` 是 core 内部结果，Factory 必须把其中三项作为一次 session candidate 使用；任一步校验或持久化失败都不能只提交定义或只提交历史记录。`AgentPorts` 持有 DefinitionProvider、ToolProvider、ToolAvailabilityProvider、HookResolver、ModelGateway、repositories、compaction ports、Skill ports、Publisher、请求级 CancellationToken、IdGenerator、TimeProvider 和 maxIterations/hardLimit 配置。它是 core 内不可变类，不进入 common 或快照。

### 关键实现逻辑

- structural precheck 只保证能安全运行 AGENT_BUILD，不要求初始 Prompt/工具已完整，因为构造 Hook可能补齐。
- AGENT_BUILD Binding 列表进入时拍快照，按 order/id 排序；当前 Hook 修改 agent-build 列表不影响本次剩余分发。
- `AgentDefinitionOperationApplier` 只能由 AGENT_BUILD 调用；其他生命周期结果类型没有该字段，dispatcher 发现自定义/反序列化结果夹带定义操作时抛 `HookContractException`。
- full validate 包含 schema 1.0.0、agentKey 一致、Prompt 非空、Hook ID 点内唯一、descriptor 匹配、Skill 可解析、default ⊆ available、available ⊆ 本次 ToolProvider 返回的健康工具名。
- 对不可用工具先比较 `existingSession.activeDefinition.availableTools`：新 session 或上一版不存在的名称属于新绑定，抛 `UnavailableToolBindingException`；上一版已存在的名称属于旧绑定，写入 `HistoricalToolBinding` 并从候选 available/default、session enabled 和请求 ToolCatalog 移出。判断以精确名称或已声明稳定来源前缀完成，禁止模糊 contains。
- 旧绑定迁移通过创建新的 candidate/session snapshot 完成，不修改 Provider 定义、旧 recovery snapshot 或对话记录；相同三元组幂等去重。健康恢复不自动重启旧绑定，普通工具漂移仍显式失败。
- 新 session 首 Turn用 snapshot.defaultEnabledTools 初始化；已有 session 新 Turn保留 enabledTools。已激活 Skill 不再 enabled 时移除并 warn；session enabled tool 普通漂移不求交集，显式失败。
- recovery snapshot 不含 defaultEnabledTools；运行 snapshot 与 recovery projection 通过独立类型避免 null/可选字段。
- 静态预检调用同一个 validator，但每个 NEW 仍重新 assemble/validate。

### 异常处理

- Provider 返回 null/未知 key：`AgentDefinitionNotFoundException`。
- 新 session/AGENT_BUILD 尝试绑定不可用工具：`UnavailableToolBindingException(toolName, origin, sourceId)`；Factory 不保存部分 session，交 runtime 按仅 END SSE 收口。
- structural/full validation：`InvalidAgentDefinitionException`，包含字段路径和全部可确定错误；不冻结部分 snapshot。
- AGENT_BUILD 普通异常 warn 后继续；返回错误结果族/非法 operation 是 `HookContractException`。
- createResumed 通过 ToolProvider 的 recovery snapshot 重载解析工具；遇到 snapshot 非 1.0.0、Hook/Tool 无法解析时失败，绝不回退最新 Provider。

### 测试方案

- `AgentDefinitionAssemblerTest` 精确断言 load、precheck、AGENT_BUILD、availability、classify、validate、freeze 顺序。
- 构造 Hook 补 Prompt、删工具、增 Hook；自改 agent-build 链不在当前请求重跑。
- 参数化覆盖非 AGENT_BUILD 结果不能修改定义/Binding，`ToolActivationDelta` 只改变 session `enabledTools`。
- dynamic Provider 在 Builder 阶段 0 次、每个 NEW 1 次；不同 agentKey 独立定义。
- createResumed 的 Provider/AGENT_BUILD 调用均 0。
- 新 session/AGENT_BUILD 不可用绑定失败；已有 session 旧绑定迁移历史后健康工具可运行；历史消息不删、模型/执行器不可见、健康恢复不自动启用；普通工具漂移失败的对照测试。

### 架构符合性

构造语义完全位于 core，定义来源和实现解析走端口；runtime 不复制规则，满足架构不变量 19。

## CORE-02 实现 11 生命周期调度器与原子结果应用

### 实现目标

建立一套 dispatcher，统一排序、解析、类型校验、普通异常策略、原子修改和流控，并替代当前两套 Hook Runtime 与万能结果。

### 涉及模块/类

源：`DefaultAgentLifecycleHookRuntime`、`DefaultAgentHookRuntime`、`AgentHookResult`、`HookDispatchResult`、`HookExecutionRecord`。

目标：`LifecycleDispatcher`、`HookResultValidator`、`HookMutationApplier`、`LifecycleDispatchOutcome`、`HookAuditLogger`。

### 核心流程

```text
select enabled bindings
  -> tool matcher when applicable
  -> sort(order,id)
  -> skip requested IDs only for PRE_TOOL_CALL resume
  -> resolve hook
  -> build immutable context view
  -> invoke
  -> validate complete result
  -> apply on temporary aggregate
  -> atomically replace state
  -> continue or return terminal outcome
```

### 接口和数据结构

```java
final class LifecycleDispatcher {
    <C extends HookContextView, R extends LifecycleHookResult>
    LifecycleDispatchOutcome dispatch(
            HookPoint point,
            ApexAgentContext context,
            Set<String> skippedBindingIds);
}
```

`LifecycleDispatchOutcome` 是 core 内 sealed 类型：Continued、EndTurn、BlockTool、DirectToolResult、HumanIntervention。它已经是验证后的控制信号，不向其他模块暴露。

### 关键实现逻辑

- Hook 普通抛错记录 session/turn/iteration/hookId/point 的 warn、trace、metric，丢弃结果并继续。
- 返回 null、descriptor 不匹配、错误结果族、非法动作、Patch 越界不属于普通异常，立即抛 HookContractException。
- 单 Hook 原子边界：消息、enabledTools、参数/结果/压缩对象在副本应用完并整体校验后一次替换。
- END_TURN 在普通生命周期停止剩余 Hook；若 Iteration 已创建，统一交 `TurnFinalizer` 执行一次 IterationEnd/TurnEnd。
- 在 ITERATION_END 返回 EndTurn 只停止剩余 iteration-end Hook 后进入 TURN_END；TURN_END 不接受 EndTurn。
- 只有 PRE_TOOL_CALL resume 可以传 skipped IDs；其他生命周期传非空集合即契约错误。
- 只有 AGENT_BUILD dispatcher 接受 `AgentBuildHookResult`/`AgentDefinitionOperation`；其他点即使通过原始类型或自定义实现绕过编译约束也必须防御性拒绝，不能把定义变化混入 `HookMutations`。
- PRE_TOOL_CALL 每次调用具体 Binding 前由 core IdGenerator 生成 `proposedInterventionId` 放入只读Context；当前 ToolCall的 `invocationId` 在开始处理该调用时只生成一次并跨挂起恢复保留。
- Hook 审计只写日志/trace/metrics；不保存通用执行列表。

### 异常处理

- HookResolver 失败与类型契约非法终止执行，因为继续会改变确定性。
- 审计系统自身失败只 warn，不影响主语义；不能让 metrics 异常回滚 Hook 修改。
- 原子应用验证失败时运行态保持调用前引用。

### 测试方案

- 11 点参数化顺序、order 相同按 id 排序、disabled/工具匹配。
- 每个点错误结果族和 TURN_END EndTurn 拒绝。
- Hook 抛错后后续 Hook 继续且前一个无任何修改。
- Message/ToolActivation/ToolCall/ToolResult/Compaction 的原子失败测试。
- END_TURN 在各点的剩余 Hook、结束 Hook次数精确断言。

### 架构符合性

Hook 实现只返回中立意图，core 独占解释与状态变更；无 Spring Bean 查找，也不持久化实现标识之外的运行历史。

## CORE-03 实现 Session、Turn、Iteration 状态编排

### 实现目标

用明确状态机表达 NEW、连续 Turn、Iteration、挂起、恢复、完成、失败和取消；修复当前 `nextTurnNo()` 可能脱离 session 以及新 Turn 清空工具/Skill 的行为。

### 涉及模块/类

源：`SuperAgentContext`、`SuperAgentSessionService`、`AgentTurn`、`AgentIteration`、`AgentExecutionStore`、SessionContextStore。

目标：`ApexAgentContext`、`SessionStateMachine`、`TurnStateMachine`、`IterationStateMachine`、`SessionSnapshotMapper`、`ExecutionPersistenceCoordinator`。

### 核心流程

NEW：

1. load session。
2. 无 session：turnNo=1，初始化工具/Skill；有 session：验证 user/agent、非 HUMAN_IN_THE_LOOP，turnNo=current+1，保留 session 状态。
3. 创建 UserMessage entryId/sortNo，先 append conversation，再 save session。
4. 保存成功后才运行 TURN_START。

Iteration 每次模型调用前创建，编号从 1 单调递增；多 ToolCall 不创建额外 Iteration。挂起只把当前 Iteration/Turn 设 SUSPENDED，Session 设 HUMAN_IN_THE_LOOP。

### 接口和数据结构

状态合法迁移由显式方法控制，例如：

```java
void startTurn(...);
void startIteration(...);
void suspend(SuspendedToolCall suspended);
void completeIteration();
void completeTurn(TurnCompletionReason reason);
void fail(Throwable cause);
void cancel();
```

不得向业务代码暴露通用 `setStatus`。ApexAgentContext 是请求内聚合，SessionSnapshotMapper 生成不可变持久化对象。

### 关键实现逻辑

- Session `COMPLETED` 仅表示上一 Turn完成；下一个合法 NEW 可将其重新置 IN_PROGRESS。
- Session `CANCELLED` 仅表示上一执行/Turn 被取消；lease 释放后的合法 NEW 可创建下一 Turn 并重新置 IN_PROGRESS，HUMAN_RESPONSE 不得恢复已取消 Turn。
- NEW 在 HUMAN_IN_THE_LOOP 时拒绝，不能隐式取消挂起。
- 挂起不设置 endedTime、不跑 TURN_END；恢复保持原 startedTime/编号。
- 模型异常将当前 Iteration、Turn、Session 一次性改 FAILED，不执行 POST_MODEL/IterationEnd/TurnEnd；best-effort 保存失败快照。
- Hook 普通异常不改状态；工具异常转换 ToolResult，不改三层为 FAILED。
- 请求级 token 在 core 检查点抛 `CancellationRequestedException` 时，当前活动 Iteration/Turn/Session 转为 CANCELLED，不运行后续 Hook、模型或真实工具。若 Assistant ToolCall 已持久化，交 CORE-06 为未完成调用补取消 ToolResult 后保存；否则直接保存状态。随后由 execution finally 发送 END/释放 lease。
- `ApexAgent.cancelBeforeRun()` 只把同步准备阶段已创建的 Session/Turn 标记 CANCELLED 并保存，不创建 Iteration、不运行 Hook/模型/工具；保存失败向 runtime 传播但不阻止其 END/lease 收口。该方法与 `run()` 的互斥由 runtime execution 状态机保证。
- 新 Turn user message append 后 session save 失败，后续不运行；重试复用稳定 entryId 的策略由调用命令/协调器保持。

### 异常处理

- user/agent 不匹配分别抛 ownership/agent mismatch 异常，不修改存储。
- 非法状态迁移抛 `IllegalExecutionStateTransitionException`，包含 from/to/action。
- Repository 失败停止后续动作；两个 Repository 不回滚，但幂等 ID 允许识别重复。

### 测试方案

- 新/既有 session 的 turnNo、iterationNo 和状态时间。
- 后续 Turn 保留 enabledTools/activatedSkills。
- HUMAN_IN_LOOP 上 NEW 拒绝，恢复不加编号。
- 四类 Repository 顺序 Fake；每一步失败后无后续调用。
- 模型异常三层 FAILED 且无结束 Hook；Hook/工具异常对比测试。
- 模型前、模型中、工具前、工具中和持久化边界取消均进入 CANCELLED；取消不误记 FAILED，不产生模型可见失败 ToolResult。
- PREPARED 取消只保存 Session/Turn CANCELLED，Iteration/Hook/模型/工具调用次数均为 0；保存失败时 runtime 仍执行 END/lease 收口。

### 架构符合性

Session/Turn/Iteration 成为唯一执行层级，删除 Stage/Mode，并通过 repository 端口持久化中立快照。

## CORE-04 建立协议事件工厂与发布语义

### 实现目标

将中立运行数据转换为纯 protocol 事件，通过请求级 Publisher 发布；core 不引用 SseEmitter，也不负责底层 END 去重。

### 涉及模块/类

源：`MessageUtils`、`ToolConfirmationMessage.from`、`ModelResponseStreamer` 的消息构造、Coordinator.sendEnd。

目标：`AgentEventFactory`、`AgentEventEmitter`（core 内薄封装）、`EventContextFactory`。

### 核心流程

- 模型 chunk -> streamContent -> publisher。
- 挂起保存成功 -> askHuman/toolConfirmation -> publisher。
- 工具 observer 事件先经过 CORE-06 allowlist，再 publisher。
- run 正常、失败或挂起退出 -> 请求发布 END。

### 接口和数据结构

```java
final class AgentEventFactory {
    StreamContentMessage streamContent(String contentId, String delta);
    AskHumanMessage askHuman(QuestionInterventionRequest request);
    ToolConfirmationMessage toolConfirmation(ToolConfirmationInterventionRequest request);
    EndMessage end();
}
```

所有运行事件 context 基础值为 `{mode:"react"}`；按事件增加 content_id、executor、invocation_id。永不输出 stage_id。

### 关键实现逻辑

- mode 是字符串协议常量，不在 common/core 建 Mode enum。
- END 使用空 EndMessage 并依赖 protocol/common mapper 的 NON_NULL，实际幂等由 runtime Once 装饰器完成。
- contentId 在一次模型调用开始时生成，所有 delta 复用；下一模型调用用新 ID。
- ToolConfirmation 的 display/editable DTO 在 kit 构造 spec 后由 factory 复制，不让 protocol 依赖 kit。
- core 可在每条已构造 Agent 的控制路径至多调用一次 `requestEnd`；同步构造失败和执行前取消由 runtime 使用同一 Once 收口，保证实际一次。

### 异常处理

- publisher 抛 AgentEventPublishException 时由 execution 发出同一请求 token 的取消命令并传播；不能只设置 observer 布尔值、warn 后继续消耗模型/执行工具。
- 事件构造缺必要 ID 是 core contract error，不发送半成品。

### 测试方案

- 复用 PRO-02 Golden File。
- react/no stage_id、contentId 聚合、确认标识一一对应。
- Fake Publisher 捕获顺序；正常/失败/挂起各请求一次 END。
- core 字节码无 SseEmitter/Servlet。

### 架构符合性

core 只依赖 protocol DTO 和事件端口，transport 由 runtime/platform 适配，符合消息出口抽象要求。

## CORE-05A 实现唯一 ReAct 控制循环

### 实现目标

实现没有 Mode/Stage 分支的单一循环，准确控制 Iteration、模型步骤、工具步骤、最大次数和结束生命周期。

### 涉及模块/类

源：`SuperAgent.executeLoop`；不迁移 `StageToolResolver`、`StageToolPlan`、`ToolInterceptor` 模式逻辑。

目标：`ApexAgent`、`ReactLoop`、`TurnFinalizer`、`MaxIterationPolicy`。

### 核心流程

采用跨模块契约第 6 节流程。每次循环：start iteration -> hooks -> CORE-05C -> CORE-05B -> 无工具结束或 CORE-06 -> iteration end。工具完成后下一次模型调用才创建下一 Iteration。

### 接口和数据结构

```java
final class ReactLoop {
    AgentRunOutcome execute(ApexAgentContext context);
}
```

内部步骤结果为 `ModelStepOutcome.FinalText`、`ToolCalls`、`EndTurn`、`Failed`；不对外公开执行模式。

### 关键实现逻辑

- maxIterations 必须 >0，由 AgentPorts runtime config 传入，默认值在 runtime 为 30。
- 最后一次基础 ModelRequest 添加一条明确系统/开发指令，内容由 runtime 默认 Prompt policy 提供，core 只调用 `FinalIterationInstructionProvider` 或读取配置文本；不能硬编码厂商消息类型。
- 最后一 Iteration 仍返回 ToolCall：不运行 PRE/POST_TOOL Hook和真实工具，CORE-06 工具结果辅助为全部调用补“达到最大轮次，强制结束”，再一次结束。
- END_TURN 的结束逻辑集中 TurnFinalizer，保证 IterationEnd/TurnEnd 至多一次且不递归。
- 模型失败路径绕过普通结束 Hook，直接返回 Failed；finally 只做资源收口，不偷偷跑生命周期。

### 异常处理

- 非法 maxIterations 构造失败。
- 核心契约/存储/压缩异常由状态机 fail 后传播；工具执行普通异常由 CORE-06 消化。
- 未识别步骤 outcome 是内部错误并终止。

### 测试方案

- 0 工具、单工具两 Iteration、多 ToolCall、Hook END_TURN。
- max=1/2 边界，模型调用数不超过 max。
- 最后一次 ToolCall 不执行工具，结果数/ID/name 完整。
- 源码/字节码无 Mode、Stage、Plan、react 分支判断。

### 架构符合性

唯一循环只编排端口和生命周期，完全移除 PlanExecutor 模式抽象。

## CORE-05B 实现模型请求、流响应与模型生命周期

### 实现目标

实现单次业务模型步骤：对基础请求执行 PRE_MODEL、硬上限、流调用、内容事件、响应聚合、POST_MODEL 和最终 assistant 消息持久化。

### 涉及模块/类

源：`ModelResponseStreamer`、`AgentPromptAssembler` 的模型部分。

目标：`ModelStepExecutor`、`ModelResponseAccumulator`、`ModelRequestHardLimitValidator`、`AssistantMessageCommitter`。

### 核心流程

```text
base request from CORE-05C
  -> PRE_MODEL_CALL
  -> apply patch + hard limit
  -> create contentId/observer
  -> ModelGateway.stream
  -> aggregate response
  -> POST_MODEL_CALL
  -> validate final ToolCalls
  -> append assistant entry
  -> save SessionSnapshot
  -> return outcome
```

保存 assistant 响应是挂起恢复的必要前置，必须在处理第一个 ToolCall 前完成。

### 接口和数据结构

`ModelStreamChunk` 至少含 content delta、finish reason、可选 tool-call delta 和 metadata。core 只为正文 delta 发送 STREAM_CONTENT；ToolCall delta 由 accumulator 汇总成完整 ToolCall 后进入 ModelResponse。

`ModelRequestHardLimitValidator` 接收最终 request 和 runtime 配置的 token hard limit；估算器由窗口/adapter提供，超限不二次压缩。

### 关键实现逻辑

- PRE_MODEL Patch 不能改 session/agent identity；可以改消息、模型 options 和工具投影。
- 模型看到的 tools 由 CORE-06 投影的 enabledTools 生成。
- ModelGateway 内部重试只复用同一个 final ModelRequest/observer，不重新进入 Hook 或 compaction。
- 调用 Gateway 前后检查 token；ModelStreamObserver 返回该 token。Gateway adapter 必须在建立 subscription 后立即注册 `dispose`，即使取消先于 subscription 创建也会立刻执行。
- POST_MODEL 可修改 response/messages，但不能改变已有 ToolCall ID/name；参数修改只在 PRE_TOOL_CALL。
- 最终 assistant entry 含 ToolCall 完整列表，先 append conversation 再 save session，成功后才能执行工具。
- 事件发布失败触发 token，Gateway adapter 主动取消 subscription；若最终以 `CancellationRequestedException` 退出则三层记 CANCELLED，其他发布故障仍按失败处理。

### 异常处理

- PRE_MODEL hard limit 超限：`ModelContextLimitException`，不调用模型，三层 FAILED。
- Gateway 最终异常：三层 FAILED，不执行 POST_MODEL、工具、IterationEnd、TurnEnd；best-effort save 后 END。
- `CancellationRequestedException`：三层 CANCELLED，不执行 POST_MODEL、工具或结束 Hook；不包装为 ModelInvocationException。
- assistant append/save 失败：停止工具，状态 FAILED。
- ToolCall ID 重复/参数 JSON 非对象：`InvalidModelResponseException`。

### 测试方案

- 流 delta/contentId 与最终正文一致；多 ToolCall delta 顺序和参数完整。
- PRE/POST 各一次，Gateway 假重试不重复生命周期。
- PRE 修改后超限不调用 Gateway。
- assistant append/save 发生在工具前；故障注入后工具 0 次。
- Gateway/Publisher 异常三层状态和结束 Hook次数。
- token 在订阅创建前/后取消均调用 dispose；core 停在最近检查点且状态为 CANCELLED。

### 架构符合性

core 只处理中立 ModelRequest/Response，真实 Spring AI 流转换由 runtime，保持厂商隔离。

## CORE-05C 实现模型调用前压缩门

### 实现目标

在每个逻辑业务模型调用前且 PRE_MODEL_CALL 之前执行一次窗口准备和条件压缩，并把最终压缩结果先持久化。

### 涉及模块/类

源：`AgentPromptAssembler.prepareWorkingMessages`、`DefaultConversationMemoryManager` 的压缩行为。

目标：`ModelRequestPreparer`、`ConversationCompactionGate`、`CompactionCommitCoordinator`。

### 核心流程

```text
WindowManager.prepare
  -> build base request(system + messages + enabled tool definitions)
  -> build check
  -> Policy.shouldCompact once
  -> false: return base
  -> PRE_MESSAGE_COMPRESSION
  -> Compactor.compact
  -> POST_MESSAGE_COMPRESSION
  -> validate result
  -> ConversationRepository.compact
  -> SessionRepository.save
  -> return request with compacted messages
```

### 接口和数据结构

`ConversationCompactionCheck` 同时保存 message/system/tool token/character estimate、total、threshold、retain count、trigger reason。`ConversationCompactionCommit` 含 stable compactionId、source sort boundary、summary、retained entry IDs 和最终 messages。

### 关键实现逻辑

- 每个 Iteration 在进入 gate 时创建一次 check；false 不运行两个 Hook。
- PRE Hook只能 Patch request（来源范围、保留策略、脱敏内容），不能伪造 result。
- POST Hook可 Patch摘要、保留消息、metadata；最终结果必须覆盖连续边界且不能丢未压缩最新消息。
- POST 返回 END_TURN 仍先提交已经生成并修订的压缩结果，再结束；PRE 返回 END_TURN 不调用 Compactor。
- `compactionId` 在首次 compact 前生成并在 Repository 重试中保持。
- Compactor 内部模型不使用业务 ModelGateway wrapper，不触发 lifecycle/gate。

### 异常处理

- Policy/Compactor/持久化异常停止本次业务模型调用并标记执行失败。
- Compactor 失败不执行 POST Hook。
- 两个 Repository 中后者失败不回滚前者；日志记录 compactionId，重试通过幂等 commit 识别已完成对话写。
- PRE_MODEL 后超限由 CORE-05B 失败，不回跳 gate。

### 测试方案

- 每 Iteration一次 shouldCompact，工具/恢复/ModelGateway 重试不额外调用。
- false、true、PRE EndTurn、Compactor failure、POST EndTurn 的完整顺序。
- Conversation save/Session save 各故障点后无 PRE_MODEL/模型。
- Compactor fake 内部调用独立模型时 gate 次数不变。
- tool/system 占用参与阈值测试。

### 架构符合性

压缩时机由 core 统一，算法和窗口实现走端口，既可观察又不把 memory 模块重新接入主链。

## CORE-06 实现工具三层状态与多 ToolCall 编排

### 实现目标

实现工具三层状态、模型投影、执行前守卫、PRE/POST Hook、进度 observer、多 ToolCall 顺序和逐结果持久化。

### 涉及模块/类

源：`ToolCallProcessor`、`ToolInterceptor`、`StageToolResolver` 的非模式工具解析、`AgentToolExecutor`。

目标：`ToolStateManager`、`ToolDefinitionProjector`、`ToolCallCoordinator`、`ToolResultFactory`、`RestrictedToolExecutionObserver`。

### 核心流程

对每个 ToolCall：

```text
set current call
  -> PRE_TOOL_CALL
  -> apply argument patch / enabled-tool delta
  -> handle terminal pre action
  -> verify tool still enabled
  -> create restricted observer
  -> AgentTool.execute
  -> convert ordinary tool exception to ToolResult
  -> POST_TOOL_CALL
  -> validate result association
  -> append result entry
  -> save session
  -> next call
```

### 接口和数据结构

`ToolCatalog` 提供本请求可执行 name -> AgentTool；`ToolStateManager` 持有 effective available/enabled 的 session 状态，并单独只读持有 historical bindings。模型请求只投影 enabled 集合中的 ToolDefinition，顺序按 available 定义顺序稳定；历史绑定绝不参与投影。

内部 `ToolCallOutcome`：Completed、Suspended、EndTurn。BLOCK/RETURN 最终都转 Completed ToolResult。

状态机合成结果由 core 内部唯一 `ToolResultFactory` 负责：

```java
final class ToolResultFactory {
    ToolResult userDenied(ToolCall call);                    // 用户拒绝执行
    ToolResult forcedEnd(ToolCall call);                     // 达到最大轮次，强制结束
    ToolResult blocked(ToolCall call, String reason);
    ToolResult disabled(ToolCall call);
    ToolResult unavailable(ToolCall call);
    ToolResult executionFailed(ToolCall call, Throwable error);
    ToolResult cancelled(ToolCall call);                     // 请求已取消，工具未执行完成
}
```

所有方法保留原 toolCallId/name；`userDenied`、`forcedEnd` 和 `cancelled` 的 metadata 固定为空，不增加 code/payload。该工厂是 core 包内实现，不进入 core-extension，kit 不提供同名或等价工厂。

### 关键实现逻辑

- Hook enable 只能选 available；disable 立即影响同一响应后续 ToolCall。如果后续 ToolCall 已被模型产生但此时禁用，不执行真实工具，生成模型可见“工具当前未启用，无法执行”结果并继续，保证一一匹配。
- PRE ToolCallPatch 只能改 arguments。POST ToolResultPatch 只能改 content/metadata，不改关联 ID/name。
- BLOCK 使用 reason 构造失败 ToolResult并运行 POST；RETURN 使用给定结果并运行 POST。
- 普通工具异常转换为当前结果，内容包含可给模型理解的脱敏摘要，不附加新 SSE 错误事件，继续剩余调用和下一 Iteration。
- Restricted observer 只允许 INVOCATION_DECLARED/CHANGE，验证 invocation/toolCall 关联后写当前请求 Publisher，并返回与 ToolExecutionContext 相同的 CancellationToken；END/交互/流内容/Artifact 直接拒绝。
- 每个 PRE Hook、真实工具和 POST Hook 前后检查 token。`CancellationRequestedException` 不转换为普通失败 ToolResult，而是转入下述统一取消补齐与终止分支；普通工具异常才转换为模型可见失败结果并继续。
- 每个结果 stable entryId，append -> save 成功后才处理下一 ToolCall。
- END_TURN 为当前及所有未完成调用补固定强制结束结果，不运行它们 PRE/POST/真实工具；已完成结果不重写。
- CORE-07C 的确认拒绝与本任务的 END_TURN 必须调用同一个 `ToolResultFactory`；禁止在分支内手写固定文案。

### 异常处理

- 找不到/禁用工具不真实执行，但补 ToolResult；已知外部工具故障固定生成内容“工具不可用”的 ToolResult。只有 availability 中有结构化记录时走该分支，普通缺失仍视为契约/配置错误。
- ToolResult ID/name 不匹配是 ToolContractException，执行失败而非交给模型。
- Observer 非 allowlist 事件抛 IllegalToolEventException，作为该工具执行异常转换 ToolResult；父 END 不受影响。
- 取消命令不是工具失败；adapter 响应取消后抛 `CancellationRequestedException`。若 Assistant ToolCall 已追加，core 不运行 PRE/POST Hook或真实工具，使用唯一工厂为当前及剩余未完成调用补“请求已取消，工具未执行完成”结果，按 ordinal 追加后把三层记 CANCELLED；已完成结果不重写，也不进入下一模型调用。
- 取消补齐使用一次 `ConversationRepository.append(cancelledEntries)` 批量按 ordinal 写入，entryId 在写前生成且重试稳定；随后一次 `SessionRepository.save` 同时保存 ToolExecutionStatus 与三层 CANCELLED。append 失败不保存快照；save 失败不回滚 append，沿用既有幂等重试/部分提交风险规则。
- append/save 失败停止剩余 ToolCall。

### 测试方案

- registered/available/default/enabled 子集、跨 Iteration/Turn 保留。
- 模型工具列表只含 enabled；伪造、过期、Hook 中途禁用均不执行。
- 历史绑定不在 ToolCatalog/模型列表；伪造同名调用得到不可用结果且执行器调用次数为 0。
- CONTINUE/BLOCK/RETURN/END_TURN 和工具异常。
- 多 ToolCall 前序持久化、后序失败/挂起，结果顺序一一对应。
- observer allowlist、关联 ID、跨请求隔离和 END 拒绝。
- ToolExecutionContext/observer token 同一实例；工具执行前/中取消时底层 command 被调用，取消后不再新增真实工具调用；当前及剩余未完成调用各有一次取消 ToolResult且不运行 POST Hook。
- 拒绝/强制结束/取消固定文本、原 ID/name、空 metadata；扫描 core/kit 证明仅 core 工厂持有三段固定文案。

### 架构符合性

core 只看 AgentTool/ToolProvider，不感知工具来源；动态启用是 session 语义，PlanExecutor 守卫完全删除。

## CORE-07A 实现人工介入挂起保存

### 实现目标

当 PRE_TOOL_CALL 请求 QUESTION/TOOL_CONFIRMATION 时，以一个 SessionSnapshot 原子保存挂起状态，再发送交互事件和本次 END。

### 涉及模块/类

源：`ToolCallProcessor.suspendForConfirmation/processAskHuman`、`PendingHumanInteraction`、`PendingToolExecution`。

目标：`InterventionSuspender`、`SuspendedToolCallFactory`、`PreToolHookProgress`。

### 核心流程

1. PRE Hook 每个成功完成后把 Binding ID加入本地有序集合；请求介入的 Hook 也加入。
2. 使用已 Patch arguments、interaction payload 和当前位置构造 SuspendedToolCall。
3. 将 Session=HUMAN_IN_THE_LOOP、Turn/Iteration=SUSPENDED、唯一挂起对象写入一次 session save。
4. save 成功后发布对应 ASK_HUMAN/TOOL_CONFIRMATION。
5. 请求 END，返回 Suspended outcome；不执行工具或结束 Hook。

### 接口和数据结构

挂起对象严格采用跨模块契约。`executedPreToolHookIds` 记录 Binding ID，不记录 hook name；同一点 ID 唯一保证跳过精确。

### 关键实现逻辑

- 新挂起替换前必须确认 session 当前没有另一个挂起对象；正常 NEW 首次介入只能为空。
- 交互事件的数据从已保存挂起对象构造，保证事件与快照一致。
- 确认的 invocationId/confirmationId 在挂起前由 IdGenerator 生成一次。
- save 前不发布任何交互事件，避免用户拿到无法恢复的提示。
- 事件发布失败时快照已挂起且无自动重放 API，这是风险 R-09；实现需记录可重放的完整 payload 和结构化错误，为后续确认的重放策略保留数据。

### 异常处理

- Session save 失败：不发交互、不 END_TURN，执行失败。
- 挂起对象字段缺失/重复 hook ID：contract error。
- Publisher 失败：传输失败并 END best-effort，保持已持久化挂起状态，不回滚。

### 测试方案

- QUESTION/CONFIRMATION 共用同一 save 路径。
- 快照字段白名单、Hook ID 顺序、arguments 使用最终 Patch 值。
- save 失败后 Publisher 0 次。
- 成功后顺序严格 save -> interaction -> END；无 Tool/Post/IterationEnd/TurnEnd。

### 架构符合性

挂起状态由 core 独占，kit 只产生结果，platform 只传输事件，符合人工介入责任边界。

## CORE-07B 实现 HUMAN_RESPONSE 恢复校验与上下文重建

### 实现目标

在不修改快照的前提下完成恢复请求全部验证，并从 recovery snapshot 重建同一 Turn/Iteration/ToolCall 的运行对象。

### 涉及模块/类

源：`HumanInLoopResumer`、`SuperAgentSessionService.resumeContext`。

目标：`HumanResponseParser`、`ResumeRequestValidator`、`ApexAgentContextRestorer`、`RestoredExtensionResolver`。

### 核心流程

```text
load session
  -> validate schema/user/agent/status/suspended point
  -> find raw submission by toolCallId
  -> parse by intervention type
  -> validate confirmationId/decision/editable keys
  -> locate ToolCall in stored model response by ID
  -> rebuild definition + hook chain + tool catalog
  -> restore enabledTools/activatedSkills/current progress
```

全部成功后才创建可运行 ApexAgent；验证阶段不 save。

### 接口和数据结构

QuestionSubmission 支持前端真实 `answers: Record<String,String|List<String>>`；AskHumanTool 最终接收规范化 JSON 文本或结构对象，由 KIT-01 定义。为兼容旧后端测试，可在 parser 接受 toolCallId 下的 scalar 并规范化为 answers["0"]，但发送协议仍以真实结构为基线。

ToolConfirmationSubmission 必须含 interaction_type=TOOL_CONFIRMATION、confirmation_id、decision；updated_args 仅 Map。

### 关键实现逻辑

- 用 toolCallId 在 IterationSnapshot.modelResponse.toolCalls 中唯一查找；找不到或重复都失败。
- confirmationId、toolCallId、intervention type 同时匹配。当前旧实现未校验 confirmationId，目标实现按前端已经回传的字段加强验证，不改协议。
- 批准时这里只验证 editable key，实际合并在 CORE-07C；拒绝也先构造 typed submission。
- Hook/Tool 从 recovery snapshot 的稳定 name 解析；不加载 Provider。
- 已知外部故障导致挂起工具不可用时，恢复器保留原 ToolCall/交互历史并构造“不可执行”上下文，由 CORE-07C 生成“工具不可用”ToolResult、迁移历史绑定后继续；普通 name 漂移仍失败。

### 异常处理

- session/user/agent/status/intervention/toolCall/confirmation 任一不匹配抛专用验证异常，快照不变。
- 非 1.0.0 snapshot 显式拒绝。
- Hook/Tool 无法解析记录缺失稳定名，不输出 options/用户正文。

### 测试方案

- 每个验证字段单独错误，Repository save 0 次、Hook/Tool/模型 0 次。
- 前端真实 ask-human/confirmation payload解析。
- toolCallId 定位而非 ordinal/index。
- 配置 Provider 改变后恢复仍用旧 snapshot；Provider 0 次。
- 生命周期调用计数证明跳过 AGENT_BUILD 至 POST_MODEL。

### 架构符合性

恢复由明确快照和 typed submission 驱动，不依赖 Web Map、Spring Bean 或最新配置，保证可恢复性和隔离性。

## CORE-07C 实现剩余 PRE Hook 五分支恢复

### 实现目标

从 `executedPreToolHookIds` 后继续当前 PRE_TOOL_CALL 链，并完整处理再次介入、END_TURN、BLOCK、RETURN、全部 CONTINUE 五个分支。

### 涉及模块/类

源：`HumanInLoopResumer.resumeAskHuman/resumeToolConfirmation` 与 `ToolCallProcessor` 的恢复辅助。

目标：`ResumedToolCallExecutor`、`ConfirmationDecisionMapper`、`SuspensionCleaner`，复用 CORE-02/06/07A。

### 核心流程

- 确认拒绝：直接构造 ReturnToolResult“用户拒绝执行”，不运行剩余 PRE Hook/真实工具，运行 POST。
- 批准/QUESTION：合并/注入 submission，跳过已执行 ID，运行剩余 PRE Hook。
- 再次介入：调用 CORE-07A 更新同一挂起槽位并累计 ID。
- END_TURN：补当前及剩余结果、清挂起、保存、结束生命周期。
- BLOCK/RETURN：生成结果、POST、append/save、清挂起后继续。
- 全 CONTINUE：重新校验 enabled，执行工具、POST、append/save、清挂起后继续。

### 接口和数据结构

editable 参数合并算法：以 suspended.resolvedArguments 为基准，只覆盖挂起 spec 的 editable keys；未声明键忽略并记录 debug，不改变参数；类型/约束验证由 ToolDefinition schema validator 在执行前完成。

再次挂起的 `executedPreToolHookIds = old IDs + 本次成功 IDs`，保持定义链顺序且去重。

### 关键实现逻辑

- rejected confirmation 在剩余 Hook 之前终止 PRE 链，避免审批后 Hook 产生副作用。
- rejected confirmation 调用 CORE-06 的唯一 `ToolResultFactory.userDenied`，不从 kit 获取结果、不在恢复分支复制文案。
- ask_human 首次 Hook ID 已保存，恢复跳过它；真实 AskHumanTool恰好执行一次并从 ToolExecutionContext 读 typed response。
- 得到任何最终 ToolResult 后先 POST Tool Hook，再 append/save，最后清除挂起；为使“结果和清除”在同一 SessionSnapshot 中，session save 使用已清除状态的快照。
- 如果 append 成功、session save 失败，重试通过 stable entryId 避免重复结果。
- 再次介入不能先清旧挂起；用新对象一次替换并保存。
- 当前 ToolCall完成后按原 ordinal 继续未完成调用，前序结果保持。

### 异常处理

- updated args schema 非法拒绝恢复并保持挂起，允许用户重新提交；不执行 Hook/工具。
- 剩余 Hook 普通异常 warn 后继续，契约异常失败。
- 工具普通异常转 ToolResult；存储失败停止剩余调用。
- 恢复前工具已转不可用时不执行剩余 PRE Hook 或真实工具；生成固定“工具不可用”结果，执行 POST、追加结果、把绑定幂等迁入历史并从 enabled 移除后继续剩余 ToolCall。
- POST Hook END_TURN 使用标准 finalizer，对剩余调用补强制结束结果。

### 测试方案

- 五分支每个至少一条完整状态/调用顺序测试。
- 批准只覆盖 editable；拒绝固定文本、无 code/payload、剩余 PRE/工具 0 次、POST 1 次。
- ask_human 工具恰好一次并读到 answers。
- 再次挂起对象替换、IDs 累计、END 一次。
- 多 ToolCall 前序结果、当前恢复、后序继续；最终挂起对象清空。
- append/save 故障与幂等重试。
- 挂起工具转不可用：历史交互/ToolCall 保留、执行次数 0、固定结果一次、historical binding 一条、健康恢复不自动启用。

### 架构符合性

统一恢复管线消除 ask_human 与工具确认的双状态机，严格恢复原 Turn/Iteration，满足核心架构不变量 4、5。
