# core-extension 模块任务

> 模块职责：只声明扩展接口，不包含任何实现、实体、枚举或 Spring 注解
> 当前总体进度：未开始；现有接口分散在单模块并引用具体框架

## EXT-01 定义模型、工具、定义、事件、存储与基础设施端口

- **任务名称**：建立 core 所需的基础扩展接口。
- **任务目标**：使 core 只能通过端口访问模型、工具、工具进度事件、定义、事件、会话、对话、Skill、ID 和时间。
- **当前进度**：未开始。现有 `IAgentDefinitionLoader` 等接口尚未形成目标端口集合。
- **设计依据**：设计文档第 5.3、7.2、13.1 节；架构文档第 5.3 节。
- **涉及范围**：`AgentDefinitionProvider`、`ModelGateway`/observer、`AgentTool`、`ToolExecutionObserver`、`ToolProvider`、`ToolAvailabilityProvider`、`AgentEventPublisher`/Factory、Session/Conversation Repository、SkillProvider/Activator、IdGenerator、TimeProvider。
- **前置依赖**：COM-01、COM-03、PRO-01。
- **具体执行内容**：
  1. 依据 common 类型定义端口签名，不携带 Spring AI、SSE、数据库或实现对象。
  2. 使事件端口只接收 protocol `AgentMessage`。
  3. 定义 `ToolExecutionObserver`，并把它作为 `AgentTool.execute` 的显式参数；ToolExecutionContext 不反向引用 core-extension，但携带 common CancellationToken。
  4. 明确 observer 本期只允许 INVOCATION_DECLARED/INVOCATION_CHANGE，不能发布 END、交互事件、流内容或其他事件，也不暴露底层 AgentEventPublisher；Model/Tool observer 暴露同一 CancellationToken，替代只能轮询的 `isCancelled()`。
  5. 区分单工具执行、工具集合解析和 Agent 定义加载。
  6. `AgentDefinitionProvider` 同时声明 `load(agentKey)` 与 `listAgents()`；后者直接返回 `List<AgentMetadata>`，不得通过逐个加载完整定义实现。
  7. `ToolAvailabilityProvider` 只返回 common 的不可变健康快照，结构化区分精确工具名、来源 scope 和普通配置漂移；端口本身不得修改定义或 session。
  8. 给每个端口编写编译契约测试或 Fake 示例，证明 core 可独立替换实现。
- **预期产出**：基础端口接口和接口契约测试。
- **验收标准**：
  - 接口参数/返回值只来自 JDK、protocol 或 common。
  - 不存在 `default` 方法、实现类、静态工厂或 Spring 注解。
  - Fake 实现可在 core 测试中使用且不启动 Spring。
  - AgentTool 可在执行期间通过 observer 上报允许的进度事件，且接口层没有 Publisher 或 SSE 依赖。
  - 默认 adapter 能向 token 注册底层取消 command；取消后注册立即执行，不能仅依赖下一次轮询。
  - Agent 列表通过 `listAgents()` 获取轻量元数据，无需 platform 读取具体 Spring 配置对象或完整 AgentDefinition。
  - availability 快照不可变；ToolProvider 发现初始化失败后先发布快照再返回健康工具，core 不会观察到“缺失但无原因”的中间态。
- **限制条件或注意事项**：数据库版 AgentDefinitionProvider 本期不实现；端口不得为当前实现便利而暴露 `ApplicationContext`、`SseEmitter`、`ToolCallback` 或 ORM 实体。

## EXT-02 定义生命周期与对话压缩端口

- **任务名称**：建立类型安全的 Hook、窗口和压缩扩展接口。
- **任务目标**：让 core 能按生命周期点解析 Hook，并在每次业务模型调用前通过可替换端口准备窗口、判断和执行压缩。
- **当前进度**：未开始。
- **设计依据**：设计文档第 5.3、8、9.2 节；架构文档第 7、8.3 节。
- **涉及范围**：`LifecycleHook<C,R>`、`HookResolver`、ConversationWindowManager、ConversationCompactionPolicy、ConversationCompactor。
- **前置依赖**：COM-02、COM-03。
- **具体执行内容**：
  1. 用泛型上下文和结果族定义 LifecycleHook。
  2. HookResolver 通过 HookPoint 与稳定注册名解析，不使用 Bean 名语义。
  3. 定义窗口、压缩判断和压缩器接口，区分业务模型入口与摘要基础设施模型调用。
  4. 明确接口不承担 Hook 排序、异常处理和原子应用；本期不增加跨 Repository 事务端口，core 只编排调用顺序。
  5. 用 Fake 证明压缩 false/true/失败路径可独立驱动。
- **预期产出**：生命周期和压缩端口接口及编译契约测试。
- **验收标准**：
  - Hook 实现声明的 Context/Result 类型可以被注册表和 core 校验。
  - 压缩策略判断、压缩执行、窗口准备是三个可独立替换端口。
  - 接口无实现、无 Spring 注解、无 framework-specific 参数。
- **限制条件或注意事项**：不得在接口中放默认 NoOp/InMemory 实现；不得让 Compactor 复用 ApexAgent 业务模型压缩门。

## EXT-03 验证 core-extension 的纯接口边界

- **任务名称**：建立 core-extension 专项架构测试。
- **任务目标**：持续阻止实体、record、枚举、实现或框架注解进入扩展模块。
- **当前进度**：未开始。
- **设计依据**：设计文档第 4.1、21.1 节；架构文档第 4.2、18.1 节。
- **涉及范围**：core-extension 编译产物、模块 POM、FND-03A 架构测试基础设施。
- **前置依赖**：EXT-01、EXT-02、FND-03A。
- **具体执行内容**：
  1. 扫描全部顶级类型并断言为 interface。
  2. 检查无 Spring 注解、无 `default` 方法、无实现类。
  3. 检查直接项目依赖精确为 protocol、common；接口直接引用 AgentMessage 时必须直接声明 protocol，不能依靠 common 传递。
  4. 检查接口没有泄漏 Spring AI、Servlet、ORM、MCP 客户端类型。
- **预期产出**：core-extension 自动架构守卫。
- **验收标准**：
  - 模块 `test` 通过。
  - 临时加入 record、实现类或 Spring 注解时测试能失败。
  - `jdeps`、ArchUnit 或等价检查没有发现禁止依赖。
- **限制条件或注意事项**：接口使用的任何新实体必须先进入 common；不得通过内部类或嵌套实现规避“只包含接口”的约束。
