# core 模块任务

> 模块职责：实现 Agent 定义构造、Session/Turn/Iteration、生命周期、唯一 ReAct 循环、工具编排、压缩门和恢复状态机
> 当前总体进度：已完成（2026-08-02）；CORE-01～CORE-07C 已完成并通过 38 项全 Fake 专项验证，挂起、typed submission 恢复、五分支状态机、多 ToolCall、固定 ToolResult、不可用工具迁移及 Skill 激活状态持久化均已覆盖

## CORE-01 实现 AgentDefinitionAssembler 与 ApexAgentFactory

- **任务名称**：实现 Agent 定义构造、权威校验、冻结和 NEW/恢复工厂分流。
- **任务目标**：把 AGENT_BUILD、定义级校验和不可变快照冻结收敛到 core，runtime 只提供端口并调用入口。
- **当前进度**：已完成（2026-08-02）。已实现唯一 Assembler/Validator、NEW/恢复工厂分流、AGENT_BUILD 进入点快照、不可用新绑定拒绝与旧绑定历史迁移；Hook tools 定义校验支持精确名称和 `*` glob；恢复路径不访问 DefinitionProvider 或 AGENT_BUILD。
- **设计依据**：设计文档第 2.30、2.32、5.4、7.3 节；架构文档第 5.4、6.2、8.1 节。
- **涉及范围**：core `AgentDefinitionAssembler`、定义校验器、`ApexAgentFactory.createNew/createResumed`、AgentDefinitionSnapshot 恢复投影。
- **前置依赖**：COM-01～03、EXT-01～02。
- **具体执行内容**：
  1. 按“Provider 加载 → 可变草稿 → AGENT_BUILD → 不可用绑定判定 → 权威校验 → 冻结”顺序实现 Assembler；只有 AGENT_BUILD 能修改定义。
  2. 校验工具三层子集、Skill/Hook 可解析性、HookPoint/Context/Result 族、Hook ID 唯一性和 Prompt 完整性。
  3. `createNew` 每个 NEW 只执行一次 AGENT_BUILD；`createResumed` 只使用持久化定义快照，禁止执行 AGENT_BUILD。
  4. 提供可供静态定义预检复用的同一校验器，但请求期仍再次权威校验。
  5. 动态 Provider 不在 Builder 阶段加载；支持不同 agentKey 在请求时分别校验。
  6. 新 session/AGENT_BUILD 尝试绑定已登记不可用工具时拒绝构造；已有 session 的既有绑定迁移为只读历史并从有效定义和 `enabledTools` 移出，普通配置漂移仍失败。
  7. AGENT_BUILD 进入时复制原始启用 Binding，按 `(order, id)` 形成不可变分发快照；本次 Hook 对自身 Binding 的修改只进入最终定义与后续生命周期。
- **预期产出**：Assembler、Factory、唯一定义校验器和构造/恢复单元测试。
- **验收标准**：
  - 测试精确验证加载、构造 Hook、校验、冻结顺序。
  - 恢复路径 Provider 调用次数与 AGENT_BUILD 调用次数均为 0。
  - 非法工具、Skill、Hook、Prompt 或不可用新绑定在 Agent 创建前失败，且不产生部分冻结快照；旧绑定迁移只生成新的不可变快照。
  - runtime 源码不存在复制的定义级校验规则。
  - 增删当前/后续 AGENT_BUILD Binding 的测试证明本次链无重复、漏跑或递归，下一次构造才观察到修改。
- **限制条件或注意事项**：数据库 Agent 定义源不在本期；Builder 只校验注册表和基础设施装配；首版定义快照 schema 版本固定为 `1.0.0`，本期不实现跨版本兼容。

## CORE-02 实现 11 生命周期调度器与原子结果应用

- **任务名称**：统一生命周期分发、排序、类型防御、异常处理和流控。
- **任务目标**：用一套 core 调度器替代现有重复 Hook Runtime，并严格执行各生命周期允许的结果族与动作。
- **当前进度**：已完成（2026-08-02）。已实现统一 LifecycleDispatcher，覆盖 11 个生命周期的排序、descriptor/结果族校验、普通异常跳过、原子 mutation 应用与 END_TURN 流控；调度器按 Binding 构造上下文并支持精确/`*` 工具匹配，typed HumanSubmission 同时传给 PRE_TOOL_CALL 与真实工具执行上下文。
- **设计依据**：设计文档第 8 节、第 21.2 节；架构文档第 7 节。
- **涉及范围**：core 生命周期调度器、Hook Binding 排序、上下文视图构造、结果校验/原子应用、错误与审计日志。
- **前置依赖**：CORE-01、COM-02、EXT-02。
- **具体执行内容**：
  1. 支持 AGENT_BUILD、TURN_START、ITERATION_START、PRE/POST_MESSAGE_COMPRESSION、PRE/POST_MODEL_CALL、PRE/POST_TOOL_CALL、ITERATION_END、TURN_END。
  2. 按 Hook Binding order 和稳定 ID 分发，运行时再次校验 Context/Result 类型。
  3. 为每次调用构造携带当前 Binding 的只读 Context；工具 Binding 支持精确名称与 `*` glob。
  4. 先验证整个 record，再原子应用消息、工具、参数、结果或压缩修改；非 AGENT_BUILD 结果一律不能携带定义/Hook 链修改。
  5. 所有 Hook 执行异常统一记录 warn，丢弃当前 Hook 的全部修改并继续后续 Hook；不提供 `FAIL_FAST` 配置。
  6. 实现 END_TURN 的非递归结束语义，禁止 SKIP_ITERATION 和非法动作。
  7. 审计写日志/Tracing/Metrics，不写通用 Hook 执行历史到快照。
- **预期产出**：单一生命周期调度器、动作处理器、结构化日志和完整单元测试。
- **验收标准**：
  - 11 个生命周期的顺序和条件执行可由 Fake Hook 精确断言。
  - `TURN_END` 返回非 Continue、结果族不匹配或动作载荷非法会明确失败。
  - 单 Hook 修改失败后，消息、工具集合、参数和结果均无部分残留；运行期 Hook 尝试修改定义或 Binding 被类型系统或防御校验拒绝。
  - 每个生命周期的 Hook 异常均只产生 warn、无部分修改，并继续后续 Hook；静态类型或定义契约非法仍明确失败。
- **限制条件或注意事项**：AGENT_BUILD 只由 CORE-01 编排；PRE_TOOL_CALL 的恢复游标只由 CORE-07A 持久化；结束 Hook 不得递归重入。

## CORE-03 实现 Session、Turn、Iteration 状态编排

- **任务名称**：建立唯一执行层级和持久化状态流转。
- **任务目标**：用明确状态而非 Hook 历史表达新 Turn、Iteration、挂起、恢复、完成、失败和取消。
- **当前进度**：已完成（2026-08-02）。已实现 ApexAgentContext 中唯一 Session/Turn/Iteration 状态编排、有序 Repository 提交、失败/取消终态和执行前取消；core 消费 SkillActivator，在 activate_skill 工具结果提交时更新 session activatedSkills，重复激活幂等、后续 Turn 保留，并在新定义移除 Skill 时清理旧激活项。
- **设计依据**：设计文档第 6、10.2、16.4 节；架构文档第 6.1、8.1 节。
- **涉及范围**：`ApexAgentContext`、`AgentRuntimeContext`、Session/Turn/Iteration 创建和状态机、Session/Conversation Repository 调用。
- **前置依赖**：CORE-01、CORE-02、COM-03、EXT-01。
- **具体执行内容**：
  1. NEW 创建或续接 Session，并为每次用户输入创建单调递增 Turn。
  2. 每次业务模型调用创建新 Iteration；多 ToolCall 属于同一 Iteration。
  3. 首个 session Turn 用 defaultEnabledTools 初始化；后续 Turn 沿用 enabledTools 和 activatedSkills。
  4. 挂起保留原 Turn/Iteration，不执行 TURN_END；恢复不创建新层级。
  5. 按“追加用户消息 → 保存 SessionSnapshot”的顺序调用两个 Repository；任一步失败都停止后续执行，不承诺跨 Repository 原子回滚。
  6. 人工介入状态与当前 Turn/Iteration 放入同一个 SessionSnapshot，由单次 SessionRepository 保存。
  7. 固化异常终态：模型异常时当前 Iteration、Turn、Session 进入 `FAILED` 并立即返回；Hook 异常跳过且状态不变；工具异常形成 ToolResult 后继续。
  8. 每次执行使用 runtime 注入的同一请求级 `CancellationToken`；在模型、工具和持久化边界检查取消，取消时三层状态进入 `CANCELLED`，不误记为失败。
  9. 提供 `ApexAgent.cancelBeforeRun()`，把同步准备阶段已创建的 Session/Turn 标记 CANCELLED 并保存，不创建 Iteration或运行生命周期；并发互斥由 runtime execution 保证。
- **预期产出**：执行状态机、快照提交编排和状态转换测试。
- **验收标准**：
  - turnNo/iterationNo 在正常、多工具、挂起和恢复场景符合定义。
  - 后续 Turn 不重置 enabledTools/activatedSkills。
  - 挂起不执行 TURN_END，最终收口只执行一次。
  - 模型异常不再执行后续 Hook、工具或模型调用，三层状态均为 `FAILED`；请求仍由既有 END 收口。
  - 模型阶段取消且 Assistant entry 尚未追加时不创建 ToolResult；工具阶段取消时停止后续编排，三层状态均为 `CANCELLED`，并为已持久化 Assistant entry 中当前及剩余未完成 ToolCall 补标准取消结果以保持一一匹配。
  - PREPARED 取消只保存 Session/Turn CANCELLED，Iteration、Hook、模型和工具调用次数均为 0；保存失败不妨碍 runtime 最终 END/lease 收口。
  - CANCELLED session 允许后续 NEW 创建递增 Turn，但拒绝 HUMAN_RESPONSE 恢复已取消 Turn。
  - Repository Fake 可断言每个关键状态的调用顺序、停止条件和提交内容。
- **限制条件或注意事项**：core 不感知数据库事务技术，本期不提供跨 Session/Conversation Repository 原子事务；不得创建 Stage 或执行模式层级；模型异常不新增协议错误事件。

## CORE-04 建立协议事件工厂与发布语义

- **任务名称**：把运行态转换为纯 protocol 消息并通过事件端口发布。
- **任务目标**：移除 protocol DTO 对执行上下文的依赖，并使 core 不持有 `SseEmitter`。
- **当前进度**：已完成（2026-08-01）。已实现纯 protocol AgentEventFactory/Emitter，固定 react context、不产生 stage_id，并通过 AgentEventPublisher 发布；core 架构测试禁止 Spring、Servlet、SSE 与数据库依赖。
- **设计依据**：设计文档第 13 节；架构文档第 8.6、13 节。
- **涉及范围**：core `AgentEventFactory`、STREAM_CONTENT 聚合信息、ASK_HUMAN、TOOL_CONFIRMATION、END 发布请求。
- **前置依赖**：PRO-01/02、EXT-01、CORE-02。
- **具体执行内容**：
  1. 建立 streamContent、askHuman、toolConfirmation、end 等消息工厂。
  2. 固定 `context.mode="react"`，移除 stage_id 生产。
  3. 通过 `AgentEventPublisher` 发布，不访问 SSE、Servlet 或 platform。
  4. 正常完成、失败和挂起退出当前传输时请求 END。
  5. 将真正的 END 幂等交给 RUN-04C 的请求级 Once Publisher。
  6. Publisher 发布失败时触发同一请求的取消令牌并传播异常，促使模型、HTTP/MCP 或其他活动适配器执行已注册的取消动作。
- **预期产出**：core 事件工厂、发布适配测试和 Golden File 复用测试。
- **验收标准**：
  - 事件 JSON 与 PRO-02 Golden File 一致。
  - `ApexAgentContext` 与 core 全模块不引用 `SseEmitter`。
  - core 各结束路径至多请求一次正常 END；即使多方请求，RUN-04C 集成后仍只实际发布一次。
- **限制条件或注意事项**：END 不增加字段；兼容 DTO 的存在不代表 core 继续生产 Plan/TaskThink/StreamThink。

## CORE-05A 实现唯一 ReAct 控制循环

- **任务名称**：实现 Iteration 控制、分支和最大迭代收口。
- **任务目标**：建立删除模式分支后的唯一循环骨架，负责 Iteration 创建、模型步骤、工具步骤和 Turn 结束控制。
- **当前进度**：已完成（2026-08-01）。已实现唯一 ApexAgent ReAct 循环、Iteration/Turn 收口和最大轮次策略，最后一轮仍返回 ToolCall 时不执行真实工具并补齐固定结果。
- **设计依据**：设计文档第 9.1、21.2 节；架构文档第 8.2 节。
- **涉及范围**：`ApexAgent` 循环骨架、Iteration 控制、模型/工具步骤接口、最大 Iteration。
- **前置依赖**：CORE-02～04、EXT-01/02。
- **具体执行内容**：
  1. 实现 begin iteration、ITERATION_START、模型步骤、工具步骤、ITERATION_END 和 TURN_END 的控制骨架。
  2. 模型无工具调用时结束 Iteration/Turn，有工具调用时交给 CORE-06。
  3. 工具完成后创建下一 Iteration，不复用当前 Iteration。
  4. maxIterations 从 runtime 配置读取，默认值由 RUN-01 提供 30。
  5. 最后一个允许的 Iteration 在 ModelRequest 中加入“直接输出最终结论且不再调用工具”的约束；若模型仍返回 ToolCall，则触发 END_TURN 并补齐“达到最大轮次，强制结束”，不创建额外 Iteration。
- **预期产出**：唯一 ReAct 控制循环和基于 Fake 步骤的状态测试。
- **验收标准**：
  - 源码中只有一套 Agent 循环且无执行模式判断。
  - 无工具、单工具、多 Iteration 和最大迭代路径的生命周期次数可精确断言。
  - 最大迭代路径不会发起第 `maxIterations + 1` 次业务模型调用；最后一次仍返回工具时不执行工具并完整补齐 ToolResult。
  - `"react"` 不成为 core 模式枚举或分支条件。
- **限制条件或注意事项**：本任务不实现模型流聚合、压缩算法或工具内部编排；不得保留 PlanExecutor 兼容路径。

## CORE-05B 实现模型请求、流响应与模型生命周期

- **任务名称**：实现一次业务模型步骤。
- **任务目标**：完成最终 ModelRequest 调用、流式内容发布、响应聚合和 PRE/POST_MODEL_CALL 生命周期。
- **当前进度**：已完成（2026-08-01）。已实现 PRE/POST_MODEL_CALL、硬上限、流式正文事件、响应提交、失败/取消状态收口与请求级 CancellationToken 透传。
- **设计依据**：设计文档第 8.2、9.1、13.2、23.1 节；架构文档第 8.2 节。
- **涉及范围**：ModelRequest 编排、PRE_MODEL_CALL、ModelGateway stream、STREAM_CONTENT、ModelResponse 汇总、POST_MODEL_CALL。
- **前置依赖**：CORE-04、CORE-05A、EXT-01；RUN-02 可先用 Fake，后续适配。
- **具体执行内容**：
  1. 接收 CORE-05C 准备的基础请求并执行 PRE_MODEL_CALL。
  2. 校验最终请求硬上限后调用 ModelGateway。
  3. 聚合流片段并通过 CORE-04 保持 content_id 事件语义。
  4. 形成完整中立 ModelResponse 并执行 POST_MODEL_CALL。
  5. ModelGateway 内部重试复用同一最终请求，不重新进入生命周期或压缩门。
  6. ModelGateway 最终失败时把当前 Iteration、Turn、Session 标记为 `FAILED` 后立即返回，不执行 POST_MODEL_CALL 或结束生命周期。
  7. 调用 Gateway 前后检查请求级 token，并通过 `ModelStreamObserver.cancellationToken()` 传递同一实例；Gateway 建立 subscription 后立即注册主动 `dispose` 动作。
- **预期产出**：模型步骤组件、流聚合测试和生命周期顺序测试。
- **验收标准**：
  - 每个 Iteration 的业务模型只调用一次逻辑模型步骤。
  - 流事件与最终响应内容、ToolCall ID/顺序一致。
  - PRE/POST_MODEL_CALL 各执行一次，重试不重复执行。
  - PRE_MODEL_CALL 修改后超限时模型不执行。
  - 模型最终异常时无后续 Hook、工具或模型调用，三层失败状态和单次 END 收口可断言。
  - token 在订阅创建前或创建后取消都能调用活动 subscription 的 `dispose`；core 在最近边界退出并记录 `CANCELLED`。
- **限制条件或注意事项**：Spring AI 类型转换属于 RUN-02；模型异常直接返回，不新增 SSE 错误事件。

## CORE-05C 实现模型调用前压缩门

- **任务名称**：实现每 Iteration 一次的窗口准备和条件压缩。
- **任务目标**：在 PRE_MODEL_CALL 之前完成压缩判断、压缩 Hook、结果保存和基础请求替换。
- **当前进度**：已完成（2026-08-01）。已实现每业务模型调用一次的显式压缩门、PRE/POST 压缩生命周期、稳定 compactionId 及 Conversation→Session 有序提交。
- **设计依据**：设计文档第 9.2、21.5、23.9 节；架构文档第 8.3、11.4 节。
- **涉及范围**：ConversationWindowManager、CompactionCheck/Policy、PRE/POST_MESSAGE_COMPRESSION、Compactor、两个 Repository 的有序调用、硬上限输入。
- **前置依赖**：CORE-02/03、CORE-05A、EXT-02；RUN-03 可先用 Fake。
- **具体执行内容**：
  1. 组装基础消息和 ModelRequest，并构造包含消息、system prompt、启用工具定义占用的检查对象。
  2. 每个逻辑业务模型调用只执行一次 shouldCompact。
  3. true 时按 PRE Hook → Compactor → POST Hook 执行；false 直接返回基础请求。
  4. 先保存 ConversationRepository 压缩结果，再保存 SessionSnapshot；两步均成功后才交给 CORE-05B。
  5. 任一步失败都停止模型调用；不实现跨 Repository 原子事务或回滚。
  6. Compactor 内部模型调用不得递归进入业务压缩门。
- **预期产出**：显式压缩门、顺序测试和失败停止测试。
- **验收标准**：
  - 非模型阶段、HUMAN_RESPONSE 工具恢复和 ModelGateway 重试不触发压缩判断。
  - true/false、PRE END_TURN、Compactor 失败、POST END_TURN 路径均有精确顺序测试。
  - ConversationRepository 或 SessionRepository 保存失败时 PRE_MODEL_CALL 和业务模型均不执行。
  - 测试不要求两个 Repository 原子回滚。
- **限制条件或注意事项**：本任务只编排压缩，不实现 runtime 默认算法；PRE_MODEL_CALL 改写后不回跳压缩门。

## CORE-06 实现工具三层状态与多 ToolCall 编排

- **任务名称**：实现工具可见性、动态启用、执行守卫和多调用结果对齐。
- **任务目标**：保证模型只看见 enabledTools、执行器只执行 enabledTools，并按顺序完整处理单次模型响应中的多个 ToolCall。
- **当前进度**：已完成（2026-08-01）。已实现 registered/available/enabled 三层状态、模型投影、执行前守卫、多 ToolCall 顺序提交、受限 observer、工具异常隔离、取消批量补齐及 core 唯一 ToolResultFactory。
- **设计依据**：设计文档第 8.3～8.4、9.1、11 节；架构文档第 6.3、8.4 节。
- **涉及范围**：工具解析、模型工具定义投影、PRE/POST_TOOL_CALL、ToolCallPatch/ToolResultPatch、对话追加和进度持久化。
- **前置依赖**：CORE-01/02/03、CORE-05A/05B、EXT-01。
- **具体执行内容**：
  1. 验证三层子集并在 session 首 Turn 初始化 enabledTools。
  2. Hook 的 ToolActivationDelta 立即影响当前和后续模型调用/Turn。
  3. 模型请求只附加 enabledTools，禁用工具不得出现在工具列表中；真实执行前再次校验，阻止伪造或过期调用。
  4. 按响应顺序处理 ToolCall，支持 CONTINUE、BLOCK_TOOL、RETURN_TOOL_RESULT、END_TURN。
  5. BLOCK/RETURN 跳过真实工具但执行 POST_TOOL_CALL；普通工具异常转换为当前 ToolCall 的模型可见 ToolResult，继续处理并让模型决定后续行为。
  6. core 为每次真实工具执行创建请求级 ToolExecutionObserver，绑定当前 AgentEventPublisher；本期只允许 INVOCATION_DECLARED/INVOCATION_CHANGE。
  7. 工具发布 END、交互事件、流内容或其他禁止事件时拒绝转发；observer 不暴露底层 Publisher，也不进入快照。
  8. 每个 ToolResult 先追加 ConversationRepository，再保存 SessionSnapshot；两步成功后才继续后序调用。
  9. END_TURN 为当前和剩余 ToolCall 逐个补齐内容为“达到最大轮次，强制结束”的 ToolResult，保留原 toolCallId/name 并保持一一匹配，不增加自定义 code/payload。
  10. `ToolExecutionContext` 与 observer 暴露同一请求级 token；工具执行前后检查取消，适配器响应后抛 `CancellationRequestedException`，core 停止剩余真实 ToolCall，并为已追加的未完成调用补标准取消结果。
  11. 在 core 内提供唯一 `ToolResultFactory`，统一构造拒绝、强制结束、取消、阻断、禁用、不可用和普通执行失败等状态机合成结果；固定文案不得散落在 kit 或分支代码。
- **预期产出**：工具编排器、三层状态管理和多 ToolCall 测试。
- **验收标准**：
  - 禁用工具不进入模型请求，也无法通过伪造/过期 ToolCall 执行。
  - 参数 Patch 在执行前生效，结果 Patch 在写对话前生效。
  - 多 ToolCall 顺序、工具异常结果回传模型、失败隔离和前序结果持久化有自动测试。
  - 允许的 INVOCATION 进度事件写入当前请求；END 和非 allowlist 事件被拒绝。
  - END_TURN 后 Assistant ToolCall 与 ToolResult 数量和 ID 一一匹配。
  - 工具执行期间取消会触发底层已注册的取消动作，剩余真实工具不执行；当前及剩余未完成调用按 ordinal 生成取消 ToolResult，不运行 POST Hook或下一模型调用。
  - 取消结果一次批量 append 后再一次保存含 ToolExecutionStatus/三层 CANCELLED 的快照；append/save 故障遵循稳定 entryId 和既有部分提交规则。
  - 确认拒绝、END_TURN 和取消结果均保留原 toolCallId/name、空 metadata，并由同一个 core 工厂生成；kit 中不存在等价工厂。
- **限制条件或注意事项**：工具来源对 core 透明；本期不保证 Conversation/Session 两个保存原子回滚；MCP 隐式上下文限制由 RUN-06 实现；禁用工具通常不会被模型调用，执行前二次校验只用于防御伪造或过期 ToolCall。

## CORE-07A 实现人工介入挂起保存

- **任务名称**：统一生成并保存 QUESTION/TOOL_CONFIRMATION 挂起状态。
- **任务目标**：在 PRE_TOOL_CALL 请求人工介入时可靠保存原 Turn/Iteration/ToolCall 和已执行 Hook ID。
- **当前进度**：已完成（2026-08-02）。已实现统一 InterventionSuspender、PRE Hook Binding ID 游标、唯一 SuspendedToolCall、Session/Turn/Iteration 挂起状态及“先保存、后交互事件、再 END”顺序；QUESTION/TOOL_CONFIRMATION 共用同一保存入口，保存失败不发布交互事件。
- **设计依据**：设计文档第 10.2～10.3 节；架构文档第 6.5、8.5 节。
- **涉及范围**：HumanInterventionRequest、SuspendedToolCall、executedPreToolHookIds、SessionSnapshot 保存和交互事件。
- **前置依赖**：CORE-02/03/04/06、KIT-01/02、COM-03。
- **具体执行内容**：
  1. PRE_TOOL_CALL 每成功完成一个 Hook 就累计稳定 Binding ID，返回介入的 Hook 也计入。
  2. 构造唯一 SuspendedToolCall，保存 Hook 改写后的参数和交互载荷。
  3. 将挂起对象、HUMAN_IN_THE_LOOP、当前 Turn/Iteration 放入同一 SessionSnapshot 并一次保存。
  4. 发布 ASK_HUMAN/TOOL_CONFIRMATION 和本次传输 END。
  5. 挂起时不执行真实工具、POST_TOOL_CALL、ITERATION_END 或 TURN_END。
  6. 保存足以重建原 ASK_HUMAN/TOOL_CONFIRMATION 的完整展示 payload，供 platform 刷新状态查询使用；core 不提供事件重放执行入口。
- **预期产出**：统一挂起创建器、SessionSnapshot 保存逻辑和挂起测试。
- **验收标准**：
  - 挂起快照不含 ToolCall index、重复 enabledTools/定义快照或其他生命周期 Hook 历史。
  - 两类介入使用同一挂起结构和保存入口。
  - SessionRepository 保存失败时不发布交互事件，也不继续执行。
  - 实时发布事件与从同一快照映射的刷新回显事件规范化 JSON 一致，读取不修改状态。
- **限制条件或注意事项**：挂起保存只涉及一次 SessionRepository 操作，不引入跨 Repository 事务；事件 END 只结束本次传输。

## CORE-07B 实现 HUMAN_RESPONSE 恢复校验与上下文重建

- **任务名称**：验证恢复请求并重建原执行位置。
- **任务目标**：从 SessionSnapshot 恢复同一 Turn、Iteration 和 ToolCall，跳过已完成生命周期。
- **当前进度**：已完成（2026-08-02）。已实现 HumanResponseParser 与恢复工厂校验，按挂起介入类型解析 QuestionSubmission/ToolConfirmationSubmission，通过 toolCallId 唯一定位原调用，只从 recovery snapshot 重建定义、Hook 与工具；非法恢复在解析阶段零写入，已知不可用工具保留到恢复状态机迁移。
- **设计依据**：设计文档第 10.1、10.2、10.4 节；架构文档第 8.5 节。
- **涉及范围**：userId/agentKey/状态/交互标识校验、toolCallId 定位、定义快照、Hook/Tool 重新解析、humanResponse。
- **前置依赖**：CORE-01、CORE-07A、COM-03；RUN-04B 的 lease 可先用 Fake。
- **具体执行内容**：
  1. 校验 userId、agentKey、Session HUMAN_IN_THE_LOOP 状态、交互标识和 toolCallId。
  2. 通过 toolCallId 在原模型响应定位调用，不使用数组 index。
  3. 使用挂起前 AgentDefinitionSnapshot 重新解析 Hook/Tool，不重新加载配置或执行 AGENT_BUILD。
  4. 恢复 session enabledTools、activatedSkills 和原 Turn/Iteration。
  5. 将 humanResponse 放入本地 ToolExecutionContext，并跳过 AGENT_BUILD 至 POST_MODEL_CALL。
- **预期产出**：恢复验证器、上下文重建器和非法恢复测试。
- **验收标准**：
  - HUMAN_RESPONSE 不创建新 Turn/Iteration、不调用模型或模型前生命周期。
  - 配置源变化不影响恢复；快照中的 Hook/Tool 无法解析时明确失败。
  - 用户、Agent、状态、交互或 toolCallId 不匹配均拒绝恢复且不修改快照。
- **限制条件或注意事项**：恢复请求是新传输但不是新业务 Turn；session lease 的获取属于 RUN-04B/04C。

## CORE-07C 实现剩余 PRE Hook 五分支恢复

- **任务名称**：继续未执行 PRE_TOOL_CALL Hook 并完成恢复收口。
- **任务目标**：完整实现再次介入、END_TURN、BLOCK_TOOL、RETURN_TOOL_RESULT 和全部 CONTINUE 五类路径。
- **当前进度**：已完成（2026-08-02）。已实现再次介入、END_TURN、BLOCK_TOOL、RETURN_TOOL_RESULT、全部 CONTINUE 五分支；确认批准只合并 editable 参数，拒绝复用 core 唯一 ToolResultFactory，ask_human typed submission 传入真实工具，并覆盖多 ToolCall 前后序、不可用迁移及稳定 entryId 重试。
- **设计依据**：设计文档第 10.4～10.5、21.3 节；架构文档第 8.5 节。
- **涉及范围**：Hook 跳过/累计、参数合并、五分支动作、工具执行、POST_TOOL_CALL、挂起清理和剩余 ToolCall。
- **前置依赖**：CORE-07B、CORE-06、KIT-01/02。
- **具体执行内容**：
  1. 跳过 executedPreToolHookIds，只按顺序执行剩余 PRE Hook。
  2. 再次介入时更新唯一挂起对象、累计 Hook ID并结束本次传输。
  3. END_TURN 时为当前和剩余调用补齐“达到最大轮次，强制结束” ToolResult、清除挂起并执行一次结束生命周期。
  4. BLOCK/RETURN 跳过真实工具，执行 POST_TOOL_CALL 后清除挂起。
  5. 全部 CONTINUE 时重新校验 enabledTools，执行真实工具和 POST_TOOL_CALL。
  6. 工具确认批准只合并允许编辑参数；拒绝映射 RETURN_TOOL_RESULT，并调用 CORE-06 唯一工厂生成模型可见“用户拒绝执行”结果。
  7. ask_human 真实工具读取 humanResponse；收口后继续剩余 ToolCall/Iteration。
- **预期产出**：五分支恢复执行器和完整恢复回归测试。
- **验收标准**：
  - 已执行 PRE Hook 不重复，未执行 Hook 顺序不变。
  - 五类分支、批准/拒绝、ask_human、多 ToolCall 前序结果均有自动测试。
  - 再次介入后挂起状态仍存在；最终完成后 SuspendedToolCall 和 Hook ID 完全清除。
- **限制条件或注意事项**：拒绝和终止 ToolResult 均保留原 toolCallId/name，不增加自定义 code/payload；本任务不新增模型调用或压缩判断。
