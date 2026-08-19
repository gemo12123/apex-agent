# Hook 机制参考

本文说明当前 `apex-agent/` 后端的 Hook 类型体系、注册与绑定、11 个生命周期点、人在回路恢复语义和内置实现。本文描述当前源码事实；历史设计与实施记录不能替代本文。

## 定位与边界

Hook 是 core ReAct 编排链中的同步拦截点，不是独立事件总线，也不是第二套 Agent 执行器。

- `common` 定义生命周期点、只读 Context、结果族、Mutation 和 Patch。
- `core-extension` 定义 `LifecycleHook<C, R>` 与 `HookResolver` 端口。
- `core` 负责定义校验、排序、分发、状态变更、提前结束和挂起/恢复。
- `kit` 提供工具确认、`ask_human`、结果截断和 Skill 激活等可选实现。
- `runtime` 按生命周期点和稳定名称注册 Hook。
- `platform` 把 Spring Bean 注册到 runtime，并从 Agent 配置读取 Binding。

Hook 只能接收 common 中定义的只读 Context。对 Agent 状态的修改必须通过结果对象声明，由 core 校验并应用。Hook 自身仍可能访问外部系统，因此外部副作用不受 core 回滚保护。

唯一例外是显式的 Session 共享数据通道：所有 `HookContextView` 都提供 `sharedData()`。它不允许直接改写 Agent 定义、模型请求、工具状态或对话历史，只用于 Hook 与原生 `AgentTool` 交换可持久化的 JSON 数据。写入立即生效，后续 Hook/工具可见；Hook 随后失败不会回滚已经完成的共享数据写入。

关键源码入口：

- 生命周期枚举：[`HookPoint`](../../apex-agent/common/src/main/java/org/gemo/apex/common/hook/HookPoint.java)
- Hook 接口：[`LifecycleHook`](../../apex-agent/core-extension/src/main/java/org/gemo/apex/extension/hook/LifecycleHook.java)
- 定义校验：[`AgentDefinitionValidator`](../../apex-agent/core/src/main/java/org/gemo/apex/core/definition/AgentDefinitionValidator.java)
- 构造期分发：[`AgentDefinitionAssembler`](../../apex-agent/core/src/main/java/org/gemo/apex/core/definition/AgentDefinitionAssembler.java)
- 运行期分发：[`LifecycleDispatcher`](../../apex-agent/core/src/main/java/org/gemo/apex/core/lifecycle/LifecycleDispatcher.java)
- ReAct 编排：[`ApexAgent`](../../apex-agent/core/src/main/java/org/gemo/apex/core/agent/ApexAgent.java)
- 工具批次：[`ToolCallCoordinator`](../../apex-agent/core/src/main/java/org/gemo/apex/core/tool/ToolCallCoordinator.java)

## 注册、绑定与解析

Hook 生效需要同时完成“注册实现”和“绑定定义”。

### 注册实现

runtime 使用 `(HookPoint, LifecycleHook.name())` 作为注册键：

```java
builder.registerHook(new ToolConfirmHook());
```

每个 Hook 实例通过 `name()` 提供唯一规范名称，不支持注册别名。同一生命周期点内名称不能重复。`HookRegistry` 只负责解析，不负责排序。

platform 中的扩展实现直接声明为 `LifecycleHook` Bean，再由 `ApexAgentPlatformConfiguration` 收集到 runtime：

```java
@Bean
CustomHook customHook() {
    return new CustomHook();
}
```

`CustomHook.name()` 必须返回 Agent Binding 中 `hook` 字段使用的稳定名称。

### 绑定定义

Agent 定义通过 Binding 决定是否以及如何执行已经注册的 Hook：

```yaml
hooks:
  PRE_TOOL_CALL:
    - id: confirm-search
      hook: toolConfirmHook
      order: 10
      enabled: true
      tools: [search_*]
      options:
        title: 确认执行搜索
```

Binding 字段含义：

| 字段 | 含义 |
| --- | --- |
| `id` | 生命周期点内唯一的 Binding 标识，也是恢复时记录 PRE Hook 进度的标识 |
| `hook` | runtime 注册时使用的稳定名称；领域对象中同样保存为 `HookBinding.hook` |
| `order` | 非负执行顺序；相同时按 `id` 排序 |
| `enabled` | 是否参与实际分发 |
| `tools` | 工具名精确或 `*` 通配模式；只在 PRE/POST_TOOL_CALL 分发时用于过滤 |
| `options` | 透传给具体 Hook 的 JSON 配置，core 不解释业务含义 |

定义装配会校验：

- 同一生命周期点没有重复 Binding ID。
- `HookTypeDescriptor` 的生命周期点、Context 类型和结果族与系统契约完全一致。
- `tools` 中的模式至少能够匹配 Agent 的一个 `availableTools`。
- Hook 稳定名称能够在对应生命周期点解析。

只注册不绑定不会执行，只绑定未注册实现会导致 Agent 定义校验失败。

## 生命周期总览

新请求的主链路：

```text
AGENT_BUILD
  -> TURN_START
  -> ITERATION_START
  -> [PRE_MESSAGE_COMPRESSION -> 压缩 -> POST_MESSAGE_COMPRESSION]（按需）
  -> PRE_MODEL_CALL -> 模型调用 -> POST_MODEL_CALL
  -> [PRE_TOOL_CALL -> 工具执行 -> POST_TOOL_CALL] × N（模型返回工具时）
  -> ITERATION_END
  -> 下一次 Iteration 或 TURN_END
```

人工介入恢复请求的主链路：

```text
AGENT_BUILD
  -> 恢复挂起 ToolCall，继续未完成的 PRE_TOOL_CALL
  -> 工具执行与 POST_TOOL_CALL
  -> ITERATION_END
  -> 后续正常 Iteration 或 TURN_END
```

恢复请求不会重新执行原 Turn 的 `TURN_START`，也不会重新执行挂起 Iteration 的 `ITERATION_START`。每个 NEW 和 HUMAN_RESPONSE execution 都会从当前模板重新装配定义并执行一次 `AGENT_BUILD`。

共享数据在 HITL 挂起时随 Session 快照保存，恢复时在 AGENT_BUILD 前重建，因此恢复阶段的 AGENT_BUILD、剩余 PRE_TOOL_CALL、真实工具和 POST_TOOL_CALL 继续访问同一份数据。

## Session 共享数据

`SharedDataStore` 按字符串 key 保存 `SharedDataEntry(cleanupPolicy, value)`：

- `value` 写入时归一化为 JSON 值模型，恢复后为标准 Java 标量、`Map` 或 `List`，不恢复自定义类。
- `ITERATION_END` 在 ITERATION_END Hook 全部完成后删除。
- `TURN_END` 在 TURN_END Hook 全部完成后删除。
- `NEVER` 不自动删除并跨 Turn 保留，可由 Hook/工具显式 `remove` 或覆盖。
- 模型最终失败和手动取消会分发可用的 ITERATION_END、TURN_END，因此执行对应清理；其他未进入异常收口的 FAILED 路径仍把短生命周期数据保留到后续第一个正常同类边界。

结束 Hook 写入与当前边界相同策略的条目会在 Hook 返回后立即被清理。Store 依赖现有 Session lease 的串行执行约束，不承诺多线程安全，也不得在 Hook 返回后由后台线程继续修改。

## 通用分发规则

### 顺序和快照

运行期每次分发都从当前最终定义中取得 Binding，过滤未启用和不匹配工具的项，再按 `(order, id)` 排序为不可变快照。

`AGENT_BUILD` 由 `AgentDefinitionAssembler` 单独分发。它使用原始定义中的 AGENT_BUILD Binding 快照，因此当前构造 Hook 新增或删除 AGENT_BUILD Binding 不会改变本次构造队列；后一个构造 Hook仍能看到前一个 Hook 已应用的定义修改。

### 返回值和短路

core 在每个 Hook 返回后立即校验和应用结果。出现以下结果时，当前生命周期点不再执行后续 Binding：

- 任意支持 `EndTurn` 的结果。
- `PRE_TOOL_CALL` 的 `BlockTool`。
- `PRE_TOOL_CALL` 的 `ReturnToolResult`。
- `PRE_TOOL_CALL` 的 `RequestHumanIntervention`。

`Continue...` 结果应用成功后继续下一个 Binding。因此后面的 Hook 通常能够观察前面 Hook 已应用的请求、响应、工具参数或工具结果修改。

### 普通异常与契约错误

Hook `apply()` 抛出普通 `RuntimeException` 时，core 记录 warning、把 Turn/Iteration、HookPoint 和 Binding ID 写入 Session 运行时快照，跳过当前 Binding 并继续后续 Hook。数据库记录不包含异常堆栈或原始异常正文。

以下情况属于契约错误，不会被当作普通异常吞掉：

- descriptor、Context 或 Result 类型不匹配。
- 返回 `null` 或错误的结果族。
- 启用 `availableTools` 之外的工具。
- 消息操作目标不存在、已压缩或属于 `SUMMARY`。
- 专用 `ToolCallPatch` / `ToolResultPatch` 修改后的 ID 或工具名不关联。
- POST_MODEL_CALL 改变 ToolCall 数量、ID 或工具名。
- TURN_END 返回 Continue 以外的结果。

契约错误会沿 Agent 失败路径收口并保存失败状态。

## 持久化 Mutation 与专用 Patch

除 AGENT_BUILD 和 TURN_END 外，多数 Continue 结果包含 `HookMutations`：

- `MessageOperation`：通过 `AppendMessage`、`ReplaceMessage`、`RemoveMessage` 持久化追加、替换或删除普通对话消息。
- `ToolActivationDelta`：启用或禁用 Session 中的工具。

工具启停具有请求内即时效果。例如某个 PRE_TOOL_CALL 禁用后续工具，同一模型响应中尚未消费的 ToolCall 会看到更新后的启用状态。Hook 只能启用最终定义 `availableTools` 中存在的工具。

消息操作就是对话仓储编辑接口：

- 每个 Hook 结果中的操作按声明顺序组成一个原子 `ConversationWriteBatch`；任一目标非法时整个批次失败。
- Append 由 core 分配 `entryId/sessionId/turnNo/sortNo/createdTime`。Replace 只替换 role、messageType、content、payload，保留目标身份与顺序字段。Remove 物理删除，不重排或复用 `sortNo`。
- `operationId` 只在单个 Hook 结果中唯一，用于契约校验和错误定位，不提供跨 execution 幂等。
- `SUMMARY` 和已被摘要覆盖的原始消息不可编辑。当前正在执行或挂起的工具消息与普通活动消息遵循相同规则，可以按 entryId Replace 或 Remove。
- Core 不解析 Hook 编辑后的 ToolCall/ToolResult 消息关系，也不锁定当前工具组；是否保持工具协议完整性由声明操作的 Hook 自己负责。
- TURN_START、ITERATION_START 及模型/工具调用后的节点所做编辑会立即写入仓储和 Context 窗口，下一次模型调用自然可见。
- PRE_MODEL_CALL 的每个 Binding 提交后都从最新窗口重建 `ModelRequest.messages`，并按最新启用工具重建 `tools`；后续 Binding 因而看到前序持久化结果。
- 调用 ModelGateway 前，core 校验 `ModelRequest.messages` 与当前 `ConversationWindow.messages` 完全一致，工具定义与当前启用工具完全一致。

普通消息不再存在“本次 Turn 可见、下次 Turn 不可见”的临时形态。唯一例外是 AGENT_BUILD 的 `AppendPrefixDeveloperMessage`：它明确属于请求级上下文，保存在独立 `prefixDeveloperMessages` 字段，不进入 ConversationRepository、压缩源或 Iteration 快照。

各专用 Patch 的边界：

| Patch | 可修改内容 | 不能修改的关联信息 |
| --- | --- | --- |
| `ModelResponsePatch` | 文本、metadata、ToolCall 参数等 | ToolCall 数量、ID、工具名 |
| `ToolCallPatch` | 工具参数 | ToolCall ID、工具名、ordinal、metadata |
| `ToolResultPatch` | 结果正文、metadata | ToolCall ID、工具名 |
| `ConversationCompactionRequestPatch` | 完整压缩请求 | session 关联必须仍满足领域模型约束 |
| `ConversationCompactionResultPatch` | 摘要、保留消息、metadata | compaction 关联必须仍满足领域模型约束 |

## 构造期 Hook

### AGENT_BUILD

Context：`AgentBuildContext`，包含 sessionId、当前 Binding 和当前 Agent 定义快照。

结果族：`AgentBuildHookResult`，当前只允许 `ContinueAgentBuild`。

可执行的定义操作：

- `ReplacePrompt`：替换 Prompt，包括 system prompt 和最大迭代数。
- `AddAvailableTool` / `RemoveAvailableTool`：调整最终可用工具。
- `AppendPrefixDeveloperMessage`：追加请求级 SYSTEM 或 USER 前置消息。
- `AddHookBinding` / `RemoveHookBinding`：调整后续生命周期 Binding。

限制：

- 不能通过当前操作类型修改消息压缩配置、enabledSkills 或 SubAgent 定义。
- 新增 available tool 不等于自动启用；新 Turn 的默认启用集合仍来自定义的 `defaultEnabledTools`。
- 前置 developer message 只属于本次 execution，同一请求内的 Iteration 复用，但不写入普通对话消息或 Iteration 持久化快照。
- AGENT_BUILD 不支持 EndTurn。

## Turn 与 Iteration Hook

### TURN_START

触发时机：新 Turn 创建、用户消息和 IN_PROGRESS 快照保存后，进入第一轮 Iteration 前。

Context：`TurnStartContext`，包含当前 `SessionSnapshot`。

能力：返回 `ContinueLoop` 或 `EndTurnLoop`，适合执行 Turn 级策略、持久化消息编辑、工具启停、审计和提前结束。人工介入恢复不重复执行。

### ITERATION_START

触发时机：Iteration 创建并保存后，准备压缩和模型请求前。

Context：`IterationStartContext`，包含当前 `TurnSnapshot`。

能力：返回 `ContinueLoop` 或 `EndTurnLoop`，适合迭代预算、策略判断、持久化消息编辑和工具启停。

### ITERATION_END

触发时机：当前 Iteration 标记完成后。正常工具批次、最终文本、Hook 提前结束和人工介入恢复完成都由 `ApexAgent` 统一收口到该点；尚处于挂起状态时不会执行。

Context：`IterationEndContext`，包含当前 `IterationSnapshot`。

能力：返回 `ContinueLoop` 或 `EndTurnLoop`，适合检查本轮模型和工具执行结果、调整下一轮工具或阻止进入下一轮。

### TURN_END

触发时机：Turn 最终状态提交前。

Context：`TurnEndContext`，包含当前 `TurnSnapshot`。

结果只能是 `ContinueTurnEnd`，不能通过结果再次结束 Turn 或修改状态。适合最终审计、指标和外部通知。TURN_END 执行后，core 才把 Turn 标记为 COMPLETED 或 ENDED_BY_HOOK 并保存。

## 消息压缩 Hook

压缩 Hook 只在压缩已启用、有可压缩消息且压缩策略判定需要压缩时执行。没有触发压缩时两个点都不会调用。

### PRE_MESSAGE_COMPRESSION

Context 包含：

- 当前基础 ModelRequest。
- 消息数、token、字符统计和触发位置。
- 当前 `ConversationCompactionRequest`。

可以替换压缩请求，或在调用 Compactor 前返回 `EndTurnPreMessageCompression`。包含消息操作时，压缩 Patch 必须保持原值；core 先提交消息编辑，再从最新窗口重算模型请求、容量和压缩请求。若编辑后不再达到阈值，立即终止本次压缩。提前结束时不会调用 Compactor，也不会提交本次压缩。

### POST_MESSAGE_COMPRESSION

Context 包含原始压缩消息和 `ConversationCompactionResult`。

可以改写摘要、保留消息选择和 metadata。最终保留消息必须是压缩来源中未经修改的连续尾部；Hook 不能借此重写已有消息。

POST Hook 使用普通 `HookMutations.messageOperations` 编辑最终保留尾部。不能编辑即将被摘要覆盖的来源消息；所有 Binding 声明的消息操作与摘要、压缩标记在同一个 `ConversationWriteBatch` 中提交，任一写入失败时共同回滚。

即使后续 POST Hook 返回 `EndTurnPostMessageCompression`，本次压缩及此前 Continue Hook 已声明的追加仍先提交，然后才进入 Turn 收尾。

默认 Compactor 的内部摘要模型调用不进入主 ReAct 循环，也不会触发 PRE/POST_MODEL_CALL。

## 模型调用 Hook

### PRE_MODEL_CALL

触发时机：最终模型请求准备完成后、调用 `ModelGateway` 前。

Context：`PreModelCallContext`，包含当前 ModelRequest。

`ContinuePreModelCall` 只包含 `HookMutations`，不能临时替换 system prompt、前置消息、完整请求、工具定义或 options。普通消息通过持久化 MessageOperation 编辑，工具通过 `ToolActivationDelta` 启停；每个 Binding 后请求都会从最新窗口和启用工具重建。返回 `EndTurnPreModelCall` 时不会调用模型。

### POST_MODEL_CALL

触发时机：流式调用返回完整 ModelResponse 后、助手消息写入对话前。

Context：`PostModelCallContext`，包含当前 ModelResponse。

可以修改文本、metadata 和 ToolCall 参数等，但必须保持 ToolCall 数量及每项的 ID、工具名不变。返回 `EndTurnPostModelCall` 时不会把该响应继续提交为助手对话消息。

流式文本分片在完整响应返回前已直接发布给客户端，因此 POST_MODEL_CALL 对完整文本的修改不会撤回客户端已经收到的历史分片。

## 工具调用 Hook

### PRE_TOOL_CALL

Context：`PreToolCallContext`，包含：

- 当前 ToolCall。
- 稳定 invocation ID。
- core 预分配的 proposed intervention ID。
- 当前 Binding 与 options。
- HUMAN_RESPONSE 恢复后解析出的 typed `HumanSubmission`；新工具批次中为 null。

结果能力：

| 结果 | 行为 |
| --- | --- |
| `ContinuePreToolCall` | 修改参数并继续后续 PRE Hook |
| `BlockTool` | 不执行真实工具，由 core 生成阻断 ToolResult |
| `ReturnToolResult` | 不执行真实工具，直接使用 Hook 给出的 ToolResult |
| `RequestHumanIntervention` | 保存整个工具批次并挂起 Session |
| `EndTurnPreToolCall` | 不执行真实工具，结束当前 Turn |

BlockTool 和 ReturnToolResult 生成的结果仍会经过 POST_TOOL_CALL。EndTurnPreToolCall 会为当前模型响应中的整批 ToolCall 写入固定强制结束结果，不执行普通 POST Hook。

### 整批 PRE 预处理

同一个 ModelResponse 中的多个 ToolCall 不会“预处理一个就立即执行一个”，而是：

1. 按 ToolCall 顺序为每个调用分配稳定 invocation ID。
2. 按顺序完成各自的 PRE_TOOL_CALL Hook。
3. 记录每个调用的最终参数、处置和已执行 Binding ID。
4. 任一调用请求人工介入时，继续预处理后续 ToolCall，但不执行任何真实工具和 POST Hook。
5. 没有介入时，再按原顺序消费整批调用。

该顺序保证人工介入事件能一次展示同批的完整待处理项，也保证 ToolCall 与 ToolResult 一一对应。

完成 PRE/HITL 参数决议后，core 在真实工具消费前更新当前助手 `TOOL_CALLS` 消息：

- `payload.toolCalls[].arguments` 始终保留模型原始参数，后续模型请求仍使用该字段。
- 已完成决议的调用增加 `payload.toolCalls[].resolvedArguments`，记录 PRE Hook 与人工可编辑参数合并后的最终值。该字段只用于审计，不表示工具一定实际执行或执行成功。
- HITL 挂起和再次介入期间不写中间态；整批不再介入后一次提交。Block、直接结果、禁用以及已决议后被取消或强制结束的调用同样保留最终参数。
- 审计更新通过单个 `ConversationWriteBatch` 原子提交；提交失败时不执行真实工具。若 Hook 已删除当前工具消息或破坏其 ToolCall 身份结构，core 尊重该修改、记录 warning 并跳过审计，不重新创建消息。

### HUMAN_RESPONSE 恢复

挂起前，core 先保存 `SuspendedToolBatch`，再发布唯一 `HUMAN_INTERVENTION`。批次保存：

- ToolCall ID、工具名、ordinal、metadata 和最终参数。
- 稳定 invocation ID。
- 已完成 PRE Hook Binding ID。
- 当前处置、介入请求和 typed submission。

恢复时：

- 只接受当前介入 ToolCall ID 对应的稀疏回复，缺失回复按介入定义的默认值解析。
- 用户拒绝工具确认时直接生成拒绝结果，不再执行剩余 PRE Hook 或真实工具。
- 用户确认可编辑参数时，只合并介入定义允许编辑的键。
- 已完成 PRE Hook 按 Binding ID 跳过，从介入点之后继续。
- Hook 可再次请求人工介入，此时以新的批次状态替换原挂起对象。
- 全批不再介入后，清除挂起状态并按原顺序执行或返回结果。

恢复前会重新装配当前 Agent 定义；如果挂起期间工具变为已知不可用，core 会迁移历史工具信息、禁用该工具并生成“工具不可用”结果，而不是执行旧实例。

### POST_TOOL_CALL

触发时机：真实工具执行完成，或 PRE 阶段生成 Block、直接结果、禁用结果后；结果写入对话前。

Context：`PostToolCallContext`，包含最终 ToolCall 和当前 ToolResult。

可以：

- 修改 ToolResult 正文和 metadata。
- 启用或禁用工具。
- 声明 `SkillActivationDelta`。
- 返回 `EndTurnPostToolCall`。

Skill 激活先暂存，只有 ToolResult 成功追加到 ConversationRepository 后才应用到 Session，避免结果保存失败但 Skill 状态已经变化。

POST Hook 返回 EndTurn 时，当前 ToolResult 先按 Hook 修改后的内容提交；同批尚未消费的 ToolCall 写入固定强制结束结果，然后进入 ITERATION_END 和 TURN_END。

取消、最大轮次强制结束和 PRE_TOOL_CALL 直接 EndTurn 生成的固定结果不进入普通 POST_TOOL_CALL。取消结果提交后，core 仍以 CANCELLED 快照依次分发 ITERATION_END 和 TURN_END。

## EndTurn 收口语义

除 AGENT_BUILD 和 TURN_END 外，各生命周期结果族都提供相应的 EndTurn 结果。

core 收到 EndTurn 后统一收口：

1. 如果当前点位处于一个仍为 IN_PROGRESS 的 Iteration 内，先完成 Iteration 并分发 ITERATION_END；若它也返回 EndTurn，以后者 reason 为准。
2. 分发 TURN_END。
3. 把 Turn 标记为 ENDED_BY_HOOK 并保存。

TURN_START 提前结束时尚未创建 Iteration，因此不会分发 ITERATION_END；ITERATION_END 自身返回 EndTurn 时也不会重复分发该点。

具体点位在进入统一收口前可能有额外提交语义：

- PRE_MODEL_CALL：不调用模型。
- POST_MODEL_CALL：不提交当前助手消息。
- PRE_MESSAGE_COMPRESSION：不执行压缩。
- POST_MESSAGE_COMPRESSION：原子提交压缩结果和此前声明的持久化消息操作。
- PRE_TOOL_CALL：整批写固定结束结果。
- POST_TOOL_CALL：先提交当前结果，再为剩余调用写固定结束结果。

## kit 内置 Hook

| 实现 | 生命周期点 | 能力 |
| --- | --- | --- |
| `AvailableSkillsPromptHook` | AGENT_BUILD | 从平台最终 Skill Registry 读取元信息，按 Agent 的 `enabledSkills` 过滤并将系统提示词中的 `{skills}` 替换为 `<available_skills>` XML |
| `ToolConfirmHook` | PRE_TOOL_CALL | 首次调用请求工具确认；恢复后放行，拒绝和可编辑参数合并由 core 处理 |
| `AskHumanInterventionHook` | PRE_TOOL_CALL | 把 `ask_human` ToolCall 转换为问题介入；非法问题定义转为 BlockTool |
| `PlainTextTruncateHook` | POST_TOOL_CALL | 截断过长纯文本 ToolResult，默认上限为 4000 个 Unicode 码点 |
| `JsonTruncateHook` | POST_TOOL_CALL | 超长 JSON ToolResult 结构化截断并落盘完整原文，返回 preview + truncation_info + full_result_file |
| `SkillActivationStateHook` | POST_TOOL_CALL | 从 ToolResult metadata 读取 Skill 名并声明激活状态 |
| `TodoMiddleware` | PRE_MODEL_CALL | 维护带稳定 payload 标记的持久化 SYSTEM/TEXT Todo 上下文；状态变化时 Replace、重复时 Remove、压缩淘汰后重新 Append |
| `CompositeLifecycleHook` | 任意单一结果族 | 顺序调用相同 descriptor 的 Hook，遇到非 Continue 结果立即短路 |

`CompositeLifecycleHook` 不合并多个 Continue 结果。如果所有子 Hook 都返回 Continue，只有最后一个 Continue 结果会交给 core 应用；前面返回的 Mutation 和 Patch 不会自动累积。需要组合修改时，应实现一个明确合并结果的专用 Hook，或使用多个独立 Binding 让 dispatcher 逐项应用。

### JsonTruncateHook

`JsonTruncateHook` 在 POST_TOOL_CALL 对超长 JSON ToolResult 做结构化截断：内容较小时原样透传；超过阈值时解析 JSON 递归截断、把完整原文落盘到本地文件，并返回一个信封。它不覆盖 `PlainTextTruncateHook` 的纯文本能力，非 JSON 内容始终原样返回。

信封格式：

```json
{
  "truncated": true,
  "data_preview": { "e": [ { "k": "value-0" }, { "k": "value-1" }, { "k": "value-2" } ] },
  "truncation_info": {
    "$.e": { "type": "array", "original_length": 1000, "kept": 3 }
  },
  "full_result_file": {
    "file_path": "/abs/path/tool-<uuid>.json",
    "content_type": "application/json"
  }
}
```

截断规则：

- 数组保留前 `maxArrayElements` 个真实元素，记录 `original_length` 与 `kept`，大元素继续递归截断。
- 对象保留前 `maxObjectFields` 个字段，记录 `original_fields` 与 `kept`，字段值继续递归截断。
- 超长字符串按 Unicode 码点截断到 `maxStringLength`，记录 `original_length` 与 `kept`。
- 截断信息统一放在 `truncation_info`，以 JSONPath（`$`、`$.e`、`$.e[0]`）为键，不混入数组元素。

binding options（均在构造默认值基础上可按 binding 覆盖）：

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `maxSize` | 8000 | 触发截断的 Unicode 码点阈值 |
| `maxArrayElements` | 3 | 数组保留样例数 |
| `maxObjectFields` | 10 | 对象保留字段数 |
| `maxStringLength` | 200 | 字符串保留码点数 |
| `outputDir` | 系统临时目录 | 完整原文落盘目录 |

绑定示例：

```yaml
hooks:
  POST_TOOL_CALL:
    - id: json-truncate
      hook: jsonTruncateHook
      order: 100
      enabled: true
      options:
        maxSize: 12000
        maxArrayElements: 5
```

写盘失败时 Hook 回退为原样透传，避免在无法持久化时截断导致完整数据丢失。

## 当前默认启用状态

platform 当前默认注册 `AvailableSkillsPromptHook`、`ToolConfirmHook`、`WriteTodosTool` 和 `TodoMiddleware`，但默认 `default_agent` 没有配置任何 Hook：

```yaml
tools:
  available: []
  default-enabled: []
hooks: {}
```

因此默认配置下没有实际执行的 Hook。`AskHumanInterventionHook`、`PlainTextTruncateHook`、`SkillActivationStateHook` 及其相关工具都需要按需显式注册，并在 Agent 定义中绑定。注册实现不等于启用能力。

`AvailableSkillsPromptHook` 的稳定注册名是 `availableSkillsPromptHook`。它由 platform 使用最终聚合的 `RuntimeSkillRegistry` 注册，但仍需 Agent 显式绑定；执行时仅展示该 Agent 的 `enabledSkills`，保留 Registry 顺序并转义 XML 特殊字符。提示词没有 `{skills}` 时不读取 Registry、不修改定义；没有启用 Skill 时占位符替换为空的 `<available_skills>` 容器。示例：

```yaml
hooks:
  AGENT_BUILD:
    - id: available-skills
      hook: availableSkillsPromptHook
      order: 0
      enabled: true
```

为某个 Agent 启用 Todo 能力时，需要同时暴露工具并绑定 PRE_MODEL_CALL：

```yaml
tools:
  available: [write_todos]
  default-enabled: [write_todos]
hooks:
  PRE_MODEL_CALL:
    - id: todo-context
      hook: todoMiddleware
      order: 100
      enabled: true
```

`write_todos.todos` 是完整列表而不是增量，项目结构为 `{"content":"任务内容","status":"pending|in_progress|completed"}`。列表保存在共享数据键 `apex.todo.items`，正常或 Hook 结束的 Turn 在 TURN_END 后自动删除；HITL 挂起时保留。FAILED/CANCELLED 仍沿用共享数据的通用残留语义。

## 开发检查清单

新增或修改 Hook 时至少检查：

1. 选择的 HookPoint 是否与实际数据可用时机一致。
2. descriptor 是否使用该点位规定的精确 Context 和结果族。
3. 是否同时完成 runtime/platform 注册与 Agent Binding。
4. `order`、Binding ID 和 tools 匹配是否明确且稳定。
5. Continue Patch 是否保留 ToolCall/ToolResult 关联不变量。
6. MessageOperation 的目标 entryId 是否仍在活动窗口；若编辑工具消息，Hook 是否自行维持其所需的协议语义。
7. EndTurn 前后哪些模型消息、压缩结果或工具结果已经提交。
8. PRE_TOOL_CALL 是否覆盖多工具批次、Block、直接结果、人工挂起、恢复和再次介入。
9. POST_TOOL_CALL Skill 状态是否只在工具结果提交成功后生效。
10. 是否补充与风险相称的 common 契约测试、core 编排测试或 kit 行为测试。

相邻测试入口：

- `common/HookContractTest`
- `core/agent/LifecycleCoverageTest`
- `core/agent/ApexAgentExecutionTest`
- `core/agent/HumanInterventionExecutionTest`
- `core/agent/ApexAgentFactoryTest`
- `kit/AskHumanContractTest`
- `kit/ToolConfirmationContractTest`
- `kit/PlainTextTruncateHookTest`
- `kit/TodoMiddlewareTest`
- `kit/WriteTodosToolTest`
- `kit/MatcherAndCompositeTest`

测试命令和交付范围见[验证与交付参考](验证与交付.md)。
