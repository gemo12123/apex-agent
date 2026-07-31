# common 模块任务

> 模块职责：维护跨模块且与实现框架无关的领域实体、快照与 JSON 公共能力
> 当前总体进度：未开始；现有共享对象仍混有 Spring AI、SSE、运行对象和持久化细节

## COM-01 建立 Agent、会话、模型、工具与 Skill 中立模型

- **任务名称**：建立跨模块基础领域模型。
- **任务目标**：用中立 DTO/record 表达 AgentDefinition、Session/Turn/Iteration、模型消息、工具和 Skill，使 core-extension 与 core 不依赖具体框架类型。
- **当前进度**：未开始。
- **设计依据**：设计文档第 5.2、6、7.1、11、12 节；架构文档第 5.2、6.1～6.4 节。
- **涉及范围**：AgentDefinition/草稿/快照/元数据，请求命令，Session/Turn/Iteration 与状态，中立消息和模型流，ToolDefinition/ToolCall/ToolResult/ToolExecutionContext，工具三层状态、ToolOrigin/ToolAvailabilitySnapshot，SkillDefinition/SkillSetDefinition。
- **前置依赖**：PRO-01、FND-02。
- **具体执行内容**：
  1. 定义跨模块唯一的数据模型，保留 ToolCall ID、顺序、参数和 metadata 的无损表达能力。
  2. 明确 `registeredTools`、`availableTools`、`defaultEnabledTools`、session `enabledTools` 的不同归属。
  3. 明确 `enabledSkills` 与 session `activatedSkills` 的不同归属。
  4. 对跨边界集合使用不可变副本或防御性复制。
  5. ToolExecutionContext 只保存本地工具所需中立数据，不引用 core-extension 的 ToolExecutionObserver 或 AgentEventPublisher。
  6. 为状态转换定义明确枚举；模型异常时当前 Iteration、Turn、Session 进入 `FAILED`，Hook/工具异常不改变三层状态。
  7. 定义不可用工具的精确名称/来源 scope 快照；它只表达 MCP/SubAgent 健康事实，不把历史绑定误作第四层可执行工具状态。
- **预期产出**：common 基础领域模型和构造/不变量单元测试。
- **验收标准**：
  - common 不包含 Spring、Spring AI、Servlet、ORM 或数据库类型。
  - ToolCall/ToolResult 的 ID、名称、参数和顺序可完整往返。
  - 工具与 Skill 子集关系能被独立校验。
  - availability 集合与来源 scope 均不可变，稳定前缀匹配规则可独立测试。
  - 集合不可通过调用方引用修改内部状态。
- **限制条件或注意事项**：中立模型不得简化到丢失现有协议或 Spring AI 必需信息，也不得用 `Map<String,Object>` 代替已明确的核心领域结构。

## COM-02 定义生命周期上下文、分型结果与原子修改对象

- **任务名称**：建立 11 个生命周期点的中立契约数据。
- **任务目标**：用生命周期专用上下文、结果接口和动作 record 代替万能可空 `HookResult`，为 core 调度器提供可验证输入。
- **当前进度**：未开始。
- **设计依据**：设计文档第 8.1～8.5 节；架构文档第 7 节。
- **涉及范围**：HookPoint、HookBinding、各 HookContextView、LifecycleHookResult、结果族、动作 record、HookMutations、MessageOperation、ToolActivationDelta、专用 Patch。
- **前置依赖**：COM-01。
- **具体执行内容**：
  1. 定义 `AgentBuildHookResult`、`LoopHookResult`、压缩前后、模型前后、工具前后和 `TurnEndHookResult`。
  2. 为 CONTINUE、BLOCK_TOOL、RETURN_TOOL_RESULT、REQUEST_HUMAN_INTERVENTION、END_TURN 定义专用 record。
  3. 限制 `TURN_START`、`ITERATION_START`、`ITERATION_END` 只使用 `LoopHookResult`，`TURN_END` 只允许 Continue。
  4. 把共享消息与工具启用变更放入 `HookMutations`，把工具参数、结果、定义和压缩修改放入专用 Patch。
  5. 在构造时复制集合并校验必填载荷，支持 core “先全量校验、后原子应用”。
- **预期产出**：生命周期中立类型体系及类型/不变量测试。
- **验收标准**：
  - 不存在包含所有生命周期可选字段的万能结果对象。
  - 不属于当前生命周期的结果族可被编译期或运行期明确拒绝。
  - 必填动作载荷为 null、越界工具启用变更或非法 Patch 会在应用前失败。
  - 结果对象创建后不可被外部修改。
- **限制条件或注意事项**：目标态不存在 `SKIP_ITERATION`；Skill 集合不能通过运行期 Hook 动态增删；只有 AGENT_BUILD 结果族可以携带定义操作，其他生命周期的类型中不得出现 AgentDefinition/Hook Binding Patch。AGENT_BUILD 执行异常同样记录 warn 后跳过，最终定义仍必须通过 Assembler 校验。

## COM-03 建立快照、人工介入与版本化持久化模型

- **任务名称**：定义可恢复且与存储技术无关的快照契约。
- **任务目标**：让内存和 PostgreSQL Repository 使用同一 SessionSnapshot 语义，并保证 HUMAN_RESPONSE 恢复不依赖运行对象或配置重载。
- **当前进度**：未开始。
- **设计依据**：设计文档第 6、7.3、10、14.5、16.2～16.3 节；架构文档第 6.5、11 节。
- **涉及范围**：SessionSnapshot、活动 Turn/Iteration runtime snapshot、AgentDefinitionSnapshot 恢复投影、HumanInterventionRequest、SuspendedToolCall、SuspensionPoint、ConversationCompaction 数据。
- **前置依赖**：COM-01、COM-02。
- **具体执行内容**：
  1. 定义 SessionSnapshot 的一级状态：当前 Turn/Iteration、enabledTools、activatedSkills、活动定义快照、只读 `historicalToolBindings` 和唯一挂起对象。
  2. `SuspendedToolCall` 只保存当前工具信息、交互信息和 `executedPreToolHookIds`。
  3. 明确禁止保存 emitter、Bean、Tool 实例、客户端、ToolCall index、重复 enabledTools/定义快照和通用 Hook 历史。
  4. 为快照加入字符串版本字段，首版固定为 `1.0.0`。
  5. 只实现 `1.0.0` 的序列化 Adapter 和 round-trip 测试，不实现升级链或未知版本分支。
- **预期产出**：中立快照模型、版本化适配入口、序列化样本和不变量测试。
- **验收标准**：
  - 挂起样本能定位原 session/turn/iteration/toolCall，并且只有 PRE_TOOL_CALL Hook ID 执行进度。
  - 通过 `toolCallId` 而非数组 index 表达恢复定位。
  - 快照不包含任何禁止类型或重复状态；历史工具绑定只含中立标识/原因/时间，不含 AgentTool 实例，且不能回填 `enabledTools`。
  - `1.0.0` 版本 round-trip 保持等价。
- **限制条件或注意事项**：`defaultEnabledTools` 是初始化参数，不进入恢复投影；挂起对象不重复保存 AgentDefinitionSnapshot；跨版本升级、版本跨度和未知版本处理不在本期范围，不宣称跨版本兼容。

## COM-04 统一 Jackson JsonUtils 并提供深拷贝契约

- **任务名称**：建立全项目唯一 JSON 公共入口。
- **任务目标**：用 Jackson 统一 record、Java Time、枚举、泛型、树转换和深拷贝，逐步淘汰 Fastjson。
- **当前进度**：未开始。当前源码仍存在 Fastjson import；依赖尚未移除。
- **设计依据**：设计文档第 2.26～2.27、5.2、14.6、16.1 节；架构文档第 11.3 节。
- **涉及范围**：common `JsonUtils`、Jackson 模块配置、泛型 TypeReference API、快照 deepCopy 测试；其他模块的 Fastjson 删除由各模块迁移任务负责，最终由 CLEAN-02 收口。
- **前置依赖**：COM-01、COM-03。
- **具体执行内容**：
  1. 提供 `toJson`、`fromJson`、`toTree`、`convert`、`deepCopy`。
  2. 集中配置 Java Time、record、枚举和协议注解兼容。
  3. 用明确目标类型反序列化，不把未校验 Tree/Map 传给 core。
  4. 对嵌套 Turn、Iteration、ToolCall、Map、集合做深拷贝别名测试。
  5. 配合各模块移除 Fastjson，最终在依赖树级阻止重新引入。
- **预期产出**：common `JsonUtils`、序列化/深拷贝测试和迁移约束。
- **验收标准**：
  - 泛型集合、时间、record、枚举和 SessionSnapshot round-trip 测试通过。
  - deepCopy 后修改源对象或副本互不影响。
  - common 不注册 Spring AI、数据库或 platform 专用类型模块。
  - 最终父工程依赖树与源码搜索均无 Fastjson/fastjson2。
- **限制条件或注意事项**：protocol 的显式字段注解优先于全局命名策略；数据库与 Spring AI 类型必须先由所属模块转换为 common DTO。
