# core-extension 模块详细设计

## 模块设计定位

`apex-agent-core-extension` 只声明 core 使用的端口。所有命令、descriptor、枚举和结果 record 已在 common；本模块每个生产 `.java` 文件的顶级类型必须是 interface，不能通过嵌套类、default 方法或静态工厂夹带实现。

目标包：`definition`、`model`、`tool`、`hook`、`event`、`repository`、`conversation`、`skill`、`time`、`id`。

## EXT-01 定义模型、工具、定义、事件、存储与基础设施端口

### 实现目标

为 Agent 定义、模型、工具、进度事件、外发事件、Session/Conversation、Skill、ID 和时间建立稳定接口，使 core 测试可以全部使用 Fake，runtime/platform 可以独立替换实现。

### 涉及模块/类

源接口：`IAgentDefinitionLoader`、`SessionContextStore`、`AgentExecutionStore`、`AgentToolExecutor`、旧消息发送工具的抽象需求。源接口不直接搬迁签名，需改用 common 类型。

目标接口采用 [跨模块契约](00-跨模块契约.md) 第 3 节，并按已确认决策提供 `ToolAvailabilityProvider`，支持“不可用工具禁止新绑定、旧绑定只读留痕”的 core 判定。

### 核心流程

1. 逐个端口先写编译契约测试和最小 Fake。
2. 只在 common 缺失中立类型时回到 COM 任务补类型，不在接口模块临时建 record；端口组合可以引用同模块其他 interface。
3. 固定每个端口的所有权、同步/异步语义和异常传播。
4. core 用 Fake 完成行为后，runtime/platform 再提供真实实现。

### 接口和数据结构

关键签名：

```java
interface AgentDefinitionProvider {
    AgentDefinition load(String agentKey);
    List<AgentMetadata> listAgents();
}

interface ModelGateway {
    ModelResponse stream(ModelRequest request, ModelStreamObserver observer);
}

interface AgentTool {
    ToolDefinition definition();
    ToolResult execute(ToolCall call,
                       ToolExecutionContext context,
                       ToolExecutionObserver observer);
}

interface ToolProvider {
    List<AgentTool> loadTools(AgentDefinition definition);
    List<AgentTool> loadTools(AgentDefinitionRecoverySnapshot definition);
}

interface AgentEventPublisher {
    void publish(AgentMessage message);
}

interface SessionRepository {
    Optional<SessionSnapshot> load(String sessionId);
    void save(SessionSnapshot snapshot);
}

interface ConversationRepository {
    void append(List<AgentMessageEntry> entries);
    List<AgentMessageEntry> load(ConversationQuery query);
    void compact(ConversationCompactionCommit commit);
}

interface SkillProvider {
    List<SkillDefinition> loadSkills();
}

interface SkillActivator {
    SkillActivationResult activate(String skillName,
                                   Set<String> enabledSkills,
                                   Set<String> activatedSkills);
}
```

`ModelStreamObserver` 和 `ToolExecutionObserver` 都返回同一个请求级 `CancellationToken`，不再只暴露布尔 `isCancelled()`。adapter 必须把 subscription/future/call handle 的取消 command 注册到 token，并仍可在回调边界检查 token。`ToolExecutionObserver.onEvent` 接收 protocol AgentMessage，但 allowlist 由 core 实现检查。

`IdGenerator` 提供 `newExecutionId/newEntryId/newInvocationId/newConfirmationId/newSubSessionId/newCompactionId`，避免 core 依赖 UUID 静态调用；`TimeProvider.now()` 返回 Instant。

### 关键实现逻辑

- `AgentDefinitionProvider.listAgents()` 是独立操作，接口注释明确不得默认遍历 key 后调用 load。
- Repository 方法按单次操作一致性定义；不增加 begin/commit/UnitOfWork。
- `ToolProvider` 返回 AgentTool 列表，core 在请求内建立按名称索引的 ToolCatalog，加载时拒绝重复名；两个重载分别处理 AGENT_BUILD 后的定义候选和恢复投影，AgentTool 实例不进入快照。
- `ToolAvailabilityProvider.current()` 返回精确工具名和 `UnavailableToolSource(origin, sourceId, stableNamePrefix, reasonCode, observedAt)` 的不可变快照，只有 MCP/SubAgent 初始化失败进入该集合。它只报告事实，不自行改定义或 session。
- SkillProvider 返回完整注册集合；SkillActivator 根据 session 状态返回 common `SkillActivationResult`，同时携带 instructions 和新的不可变 activated set。

### 异常处理

- 接口不吞异常、不声明框架异常。实现用中立运行时异常包装。
- 找不到定义、工具、Skill、Hook 的异常类型在 common/core 定义；interface JavaDoc 说明何时抛出。
- Publisher/Observer 失败必须可传播并触发同一 token 的取消命令，不能像当前 MessageUtils 只 warn 后继续。

### 测试方案

- `ExtensionApiCompileTest` 用纯 JDK Fake 实现全部接口。
- 反射检查参数/返回值只属于 JDK、protocol、common或同模块其他interface，后者只用于端口组合。
- `AgentDefinitionProviderContractTest` 的 Fake 统计 load/list 调用，证明列表不必加载定义。
- `ToolAvailabilityProviderContractTest` 验证快照不可变、精确名/稳定前缀匹配和健康来源不受影响；同一调用内不得出现 ToolProvider 已剔除而 availability 尚未更新的中间态。
- Observer Fake 验证 token 身份一致、取消回调主动触发和事件回调异常可见；只轮询而不注册底层取消句柄的 adapter 契约测试失败。
- Repository Fake 验证 append/compact 命令包含稳定幂等 ID。

### 架构符合性

core 对所有外部资源只依赖接口，runtime/platform/memory 通过实现接入，完全符合依赖倒置。

## EXT-02 定义生命周期与对话压缩端口

### 实现目标

建立类型安全 LifecycleHook、HookResolver、窗口准备、压缩判定和压缩执行端口，并明确业务模型压缩门与 Compactor 内部模型调用是两个入口。

### 涉及模块/类

源：`AgentLifecycleHook`、`AgentLifecycleHookRuntime`、`PreToolCallHook`、`PostToolCallHook`、`ConversationMemoryManager`、`DialogueSummaryGenerator`。

目标接口：`LifecycleHook<C,R>`、`HookResolver`、`ConversationWindowManager`、`ConversationCompactionPolicy`、`ConversationCompactor`。

### 核心流程

1. LifecycleHook 暴露 `HookTypeDescriptor` 并实现 `apply`。
2. HookResolver 只按 HookPoint + 稳定注册名解析，不接收 Bean 名或 ApplicationContext。
3. WindowManager 从 ConversationRepository 读取窗口并返回基础消息，不执行压缩。
4. Policy 对 `ConversationCompactionCheck` 做纯判断。
5. Compactor 接收完整 request，生成 result，不回调 ApexAgent 或生命周期。

### 接口和数据结构

```java
interface LifecycleHook<C extends HookContextView,
                        R extends LifecycleHookResult> {
    HookTypeDescriptor descriptor();
    R apply(C context);
}

interface HookResolver {
    LifecycleHook<?, ?> resolve(HookPoint point, String name);
}

interface ConversationWindowManager {
    ConversationWindow prepare(ConversationWindowRequest request);
}

interface ConversationCompactionPolicy {
    boolean shouldCompact(ConversationCompactionCheck check);
}

interface ConversationCompactor {
    ConversationCompactionResult compact(ConversationCompactionRequest request);
}
```

CompactionCheck 必须含 messages/system/tools 的 token/字符估算、阈值、保留窗口和触发上下文；Policy 不能自行读取 SessionRepository。

### 关键实现逻辑

- 泛型只提供编译期帮助，注册表和 core 仍使用 descriptor 做运行时验证。
- HookResolver 不负责排序；排序只由 core 按定义 Binding 完成。
- Compactor 实现可用模型，但必须是独立依赖对象，不接受 ModelGateway 的业务压缩门包装器。
- WindowManager 不修改 compacted 标记；提交只通过 ConversationRepository.compact。

### 异常处理

- HookResolver 找不到或 descriptor 不匹配抛中立解析异常。
- Policy 异常属于基础设施失败，停止本次模型调用；不能默认为 false 隐藏超限风险。
- Compactor 失败向 core 传播，不执行 POST_MESSAGE_COMPRESSION。

### 测试方案

- Fake Hook 声明正确/错误 descriptor，验证注册和解析。
- Fake Window/Policy/Compactor 分别统计调用，支持 false/true/failure。
- 编译测试证明接口无 Spring AI、ApplicationContext、数据库类型。
- 检查 Compactor 接口没有 Agent/ApexAgent 参数，防止递归进入主循环。

### 架构符合性

生命周期和压缩被拆为独立可替换端口，core 只保留顺序与状态语义，runtime 只提供默认策略，职责与目标架构一致。

## EXT-03 验证 core-extension 的纯接口边界

### 实现目标

把“只包含接口”落实为字节码和依赖测试，防止后续把方便类、NoOp、Spring 注解或 framework-specific 参数放入扩展模块。

### 涉及模块/类

- core-extension 所有生产 class file、POM。
- FND-03A 架构测试入口。
- common/protocol API 白名单。

### 核心流程

1. 枚举模块生产源码对应的顶级 class。
2. 断言 `Class.isInterface()`。
3. 扫描 declared methods，拒绝非 abstract、非 static compiler constant accessor 的 default 实现；本设计直接禁止所有 default 方法。
4. 扫描注解和签名依赖。
5. 检查 POM 直接依赖精确为 protocol 与 common；前者由 AgentEventPublisher/ToolExecutionObserver 的 AgentMessage 签名直接使用，不能只依靠 common 的传递依赖。

### 接口和数据结构

本任务不新增生产接口。测试报告以 `ExtensionBoundaryViolation(type, rule, referencedType)` 输出，便于定位。

### 关键实现逻辑

- 用户声明的 nested class/record/enum 同样失败；不能只扫顶级源码文件。
- Java 接口中的隐式 public static final 编译期常量不应在此模块出现；常量必须迁 common/protocol，因此检测字段非空即失败。
- `jdeps` 扫描传递引用，防止泛型签名泄漏 Spring AI。

### 异常处理

- 扫描工具无法解析 JDK 25 class 时构建失败并升级工具，不跳过模块。
- synthetic lambda/bridge 需按字节码 flag 排除，但用户可见嵌套实现不能豁免。

### 测试方案

- fixture 临时加入 record、default method、`@Component`、Spring AI 参数，逐条验证规则失败。
- 模块独立 `test`、父 reactor `test` 均自动执行。
- 依赖树断言直接项目依赖只有 protocol、common，且 dependency analyze 无 used-but-undeclared。

### 架构符合性

自动边界使 core-extension 永远保持端口层，避免实现反向成为 core 的隐式基础设施。
