# common 模块详细设计

## 模块设计定位

`apex-agent-common` 是协议之上的中立领域层。它只定义跨 core-extension、core、runtime、platform 和 memory 共享的数据，不承载端口实现、数据库实体或 Spring AI 类型。详细字段的共享基线见 [跨模块契约](00-跨模块契约.md)。

目标包：`agent`、`execution`、`message`、`model`、`tool`、`skill`、`hook`、`intervention`、`snapshot`、`conversation`、`json`、`exception`。

## COM-01 建立 Agent、会话、模型、工具与 Skill 中立模型

### 实现目标

建立唯一的跨模块领域词汇，替换当前混合了 `SuperAgentContext`、Spring AI Message、ToolCallback、Plan、Memory 和传输状态的对象模型，并确保 Spring AI 适配时 ToolCall/ToolResult 不丢失。

### 涉及模块/类

源映射：

- `definition/agent/AgentDefinition` -> common `AgentDefinition`、`AgentMetadata`、Prompt/压缩/工具/Skill 定义。
- `context/SuperAgentContext` -> `SessionSnapshot` 的基础字段与 core `ApexAgentContext`，本任务只实现中立部分。
- `hook/lifecycle/AgentTurn`、`AgentIteration`、`ToolCallRecord` -> common execution records。
- Spring AI Message/ToolCall 仅作为 RUN-02 映射源，不搬入 common。
- `constant/ExecutionStatus` -> `SessionStatus`；Turn/Iteration 各自独立状态枚举。

### 核心流程

1. 先定义 ID、枚举和最小 record，再定义包含关系，避免用 Map 代替未稳定的核心对象。
2. 从当前 Spring AI 样本提取必须保留字段：role、text、toolCallId、name、arguments、ordinal、ToolResult 关联和必要 metadata。
3. 在构造器中校验必填字段并复制所有嵌套集合。
4. 为定义态、session 态和请求态建立不同类型，不把 defaultEnabledTools、enabledTools 混在同一对象。
5. 为本地 ToolExecutionContext 只保留显式中立信息和可选 human submission，不引用 observer/publisher。

### 接口和数据结构

核心 record 采用跨模块契约中的定义，另补充：

```java
record AgentRequest(String sessionId, String agentKey, String userId, String query) {}
record AgentExecutionDescriptor(String executionId, String sessionId,
                                String agentKey, String userId, RequestKind kind) {}
record ToolDefinition(String name, String description, String inputSchemaJson,
                      Map<String, Object> metadata) {}
record ToolExecutionContext(String sessionId, long turnNo, int iterationNo,
                            String userId, HumanSubmission humanSubmission,
                            SubAgentCallTrace subAgentCallTrace,
                            Map<String, Object> attributes) {}
record SkillDefinition(String name, String description, String instructions,
                       Map<String, SkillResourceDescriptor> resources) {}
record SubAgentCallTrace(String traceId, List<String> agentKeys, int maxDepth) {}
```

只有工具 arguments、vendor metadata 和 Hook options 使用 `Map<String,Object>`；Session、ToolCall、人工介入等已知结构不得退化为 Map。

### 关键实现逻辑

- `ToolCall.ordinal` 在一个 ModelResponse 内从 0 连续递增；构造 ModelResponse 时校验 toolCallId 唯一。
- 根Agent创建 `SubAgentCallTrace(traceId, List.of(agentKey), maxDepth)`；每次远程子调用返回新trace并追加目标agentKey，不把该对象写入前端协议。
- ToolResult 的 toolCallId/name 必须与原调用一致，core 在追加前再次校验。
- `ToolSetDefinition` 校验 defaultEnabled ⊆ available；registered 子集要依赖运行注册表，留给 core Assembler。
- session enabledTools、activatedSkills 用保持确定顺序的不可变集合；建议内部 `LinkedHashSet` 后复制为 unmodifiableSet，序列化顺序稳定。
- `AgentMessageEntry.payload` 不允许 null Map，外部“无 payload”由 record 使用空 Map，数据库 Adapter 写 null 以节省存储时做映射转换。
- 模型异常只由 core 状态机把三层改为 FAILED；common 枚举本身不实现状态迁移方法，避免隐藏副作用。

### 异常处理

- record 构造参数非法抛 `IllegalArgumentException`，信息带字段名；跨聚合不变量由 `DomainInvariantException` 表示。
- metadata 中无法被 Jackson 序列化的对象在进入 common 边界时立即拒绝，不能等到持久化才失败。
- Spring AI 专有字段无法表达时先扩充中立 metadata 契约并补 round-trip 测试，不泄漏厂商类型。

### 测试方案

- `ToolCallRoundTripTest`：多工具顺序、ID、参数、metadata。
- `ToolSetDefinitionTest`、`SkillSetDefinitionTest`：子集与重复名。
- `ImmutableDomainModelTest`：修改输入集合/Map 或 getter 返回对象不影响 record。
- `ModelConversationRoundTripTest`：user/assistant/tool 全角色消息。
- 架构测试确保 common 无 Spring/Spring AI/Servlet/ORM import。

### 架构符合性

中立模型切断 core 对 Spring AI 和数据库的依赖，同时用明确类型保持领域语义，是八模块依赖图成立的前提。

## COM-02 定义生命周期上下文、分型结果与原子修改对象

### 实现目标

为 11 个 HookPoint 定义只读上下文、专用结果族和 operation/patch，替换当前 `AgentHookResult` 的可空字段组合、`HookFlowAction` 和 `SKIP_ITERATION`。

### 涉及模块/类

源类型：`hook/lifecycle/HookPoint`、`AgentHookContext`、`AgentHookResult`、`HookFlowAction`、`MessageOperation`、tool hook context/result、配置 `HookBindingConfig`。

目标包：`common.hook.context`、`common.hook.result`、`common.hook.operation`。

### 核心流程

1. 定义 HookPoint 与 `HookContextView`、`LifecycleHookResult` 标记接口。
2. 为每个生命周期建立专用 Context record，只暴露该阶段允许读取的快照。
3. 为每个动作建立独立 record，让 Java 类型表达流控，而非再加一个万能 action enum。
4. Operation/Patch 构造时做局部校验；整体状态校验仍由 core 原子应用器完成。
5. 提供 `HookTypeDescriptor`，解决运行注册表在泛型擦除后无法可靠判断 Context/Result 类型的问题。

### 接口和数据结构

关键结构：

```java
record HookTypeDescriptor(
        HookPoint hookPoint,
        Class<? extends HookContextView> contextType,
        Class<? extends LifecycleHookResult> resultType) {}

record HookMutations(
        List<MessageOperation> messageOperations,
        ToolActivationDelta toolActivationDelta) {}

record ToolActivationDelta(Set<String> enable, Set<String> disable) {}
```

专用 Patch：`ToolCallPatch`、`ToolResultPatch`、`ModelRequestPatch`、`ModelResponsePatch`、`ConversationCompactionRequestPatch`、`ConversationCompactionResultPatch`。Patch 只携带允许修改字段；例如 ToolCallPatch 只允许替换 arguments，不允许改 toolCallId/name。

`PreToolCallContext` 还包含当前 ToolCall 的稳定 `invocationId` 和本次 Binding 调用预分配的 `proposedInterventionId`。Hook 若请求确认，必须使用后者作为 confirmationId；这样 kit 不依赖 UUID/IdGenerator，core 仍能在测试中提供确定性 ID。未请求介入时该预分配 ID 可以废弃。

结果族和合法动作严格采用跨模块契约表。`TurnEndHookResult` 只有 `ContinueTurnEnd`。

### 关键实现逻辑

- MessageOperation 用 sealed interface：Append、Insert、Replace、Remove；index 以当前 Hook 输入快照为基准，同一个 Hook 多个 operation 按声明顺序应用到临时副本。
- 禁止同一 ToolActivationDelta 同时 enable/disable 同一名称。
- RequestHumanIntervention 必须带非空 request；BlockTool 必须有非空 reason；ReturnToolResult 必须带与当前 ToolCall 匹配的结果，匹配检查由 core 做。
- Context 内集合和模型对象都是不可变快照；Hook 无法直接修改运行态。
- AGENT_BUILD 只接受 AgentDefinitionOperation，不接受 HookMutations。

### 异常处理

- null 必填载荷、重复操作键、负 index 在 record 工厂阶段拒绝。
- 结果族错误属于 `HookContractException`，不是可忽略的 Hook 执行异常。
- 运行期 enable 不在 availableTools 的名称由 core 整体校验并拒绝整个 Hook 修改。

### 测试方案

- 为每个 HookPoint 参数化验证合法/非法结果族。
- 验证 TURN_END 无法返回 END_TURN，目标源码不存在 SKIP_ITERATION。
- `HookMutationAtomicityFixture` 验证一个 Patch 后半部分非法时原对象完全不变。
- 序列化测试确保 Hook Binding ID/name/order/options 可进入定义快照。
- 不可变性、null、越界和同名 enable/disable 测试。

### 架构符合性

Hook 只通过 common 数据表达意图，core 统一解释，kit/runtime 实现不能直接操作 core 上下文，符合接口驱动和扩展可替换原则。

## COM-03 建立快照、人工介入与版本化持久化模型

### 实现目标

定义内存与 PostgreSQL 共用的恢复快照，使 HUMAN_RESPONSE 在配置变化、进程重启和请求级 Publisher 更换后仍能恢复同一 Turn/Iteration/ToolCall。

### 涉及模块/类

源类型：`SuperAgentContext` 可持久化字段、`PendingHumanInteraction`、`PendingToolExecution`、`AgentTurn`、`AgentIteration`、`memory/model/SessionRuntimeSnapshot`。

目标：`SessionSnapshot`、`TurnSnapshot`、`IterationSnapshot`、`AgentDefinitionRecoverySnapshot`、`SuspendedToolCall`、`HumanInterventionRequest`、压缩 check/request/result/commit。

### 核心流程

1. 先从恢复算法反推必须字段，不把当前上下文所有字段整体序列化。
2. 挂起前保存当前 ModelResponse、全部 ToolCall、已完成 ToolResult、当前 ToolCall 和 stable pre-hook IDs。
3. 将活动定义冻结成 recovery snapshot，与 session 工具/Skill 状态分别保存。
4. 通过 versioned adapter 序列化为显式 DTO；Repository 不直接 serialize core 上下文。
5. load 后先验证 schemaVersion 和聚合不变量，再交 core。

### 接口和数据结构

除跨模块契约外：

```java
record IterationSnapshot(
        int iterationNo,
        IterationStatus status,
        ModelRequest modelRequest,
        ModelResponse modelResponse,
        List<ToolResult> completedToolResults,
        Instant startedTime,
        Instant endedTime) {}

record TurnSnapshot(
        long turnNo,
        TurnStatus status,
        IterationSnapshot currentIteration,
        Instant startedTime,
        Instant endedTime) {}
```

不重复存对话正文历史；Iteration modelRequest 可以只保留恢复必需投影，但必须含原 assistant ToolCall 顺序。`SuspendedToolCall.executedPreToolHookIds` 用 List 保序且构造时校验不重复。

### 关键实现逻辑

- `schemaVersion` 使用常量 `SnapshotSchemaVersion.V1 = "1.0.0"`；常量类位于 common snapshot 包。
- 读取非 1.0.0 抛 `UnsupportedSnapshotVersionException`。这不是升级兼容实现，而是防止 silent corruption 的最低安全行为。
- recovery snapshot 解析 Hook name/id/options，但不解析实现对象；Tool/Hook 实例由 runtime registry 重建。
- defaultEnabledTools 不进入 recovery snapshot；activeDefinition.availableTools 仍必须保留，用于恢复时校验 session enabledTools。
- ToolCall 用 ID 定位；ordinal 只用来继续剩余调用顺序。
- 人工提交类型与挂起类型必须匹配，确认 ID/toolCall ID 必须同时校验。

### 异常处理

- snapshot 缺失活动 Turn、挂起状态和 SessionStatus 不一致、ToolCall ID 找不到均抛 `InvalidSnapshotException`，不修补后继续。
- Hook/Tool 名在 registry 中无法解析属于恢复配置错误，core 拒绝恢复，不重新加载最新 AgentDefinition。
- 反序列化 JSON 错误包装为 `SnapshotDecodingException` 并保留 sessionId/版本，不记录敏感正文。

### 测试方案

- 1.0.0 完整 round-trip 与 JSON 样本测试。
- 禁止字段扫描：SseEmitter、Spring Bean、Tool 实例、客户端、index、通用 Hook 历史均不存在。
- 多 ToolCall 前序完成、后序挂起样本恢复定位。
- 重复 pre-hook ID、定义/工具重复状态、错误确认 ID 测试。
- 未知版本显式拒绝测试，不做升级成功测试。

### 架构符合性

快照只含中立恢复投影，避免 platform 表结构和 runtime 对象进入 core，同时满足进程重启恢复与请求级资源隔离。

## COM-04 统一 Jackson JsonUtils 并提供深拷贝契约

### 实现目标

建立全项目唯一的 Jackson 入口，替换当前 `util/JacksonUtils` 中夹带 Spring AI deserializer 的设计，并为内存 Repository 提供可靠深拷贝。

### 涉及模块/类

- 源：`util/JacksonUtils`、`MessageDeserializer`、`ChatResponseDeserializer`、两处 Fastjson 调用。
- 目标：`org.gemo.apex.common.json.JsonUtils`、`JsonException`。
- Spring AI deserializer 移至 runtime Adapter；数据库映射留 platform。

### 核心流程

1. 构建无框架 ObjectMapper：JavaTime、record、NON_NULL、未知字段容忍、日期非时间戳。
2. 提供统一静态 API和包可见 mapper builder，禁止业务模块各自改变全局规则。
3. deepCopy 采用 `convertValue(value, targetType)` 或 serialize/deserialize；对泛型使用 JavaType/TypeReference。
4. protocol Golden File 和 common snapshot 同时跑回归。
5. 各模块迁移时逐处删除 Fastjson，CLEAN-02 删除依赖。

### 接口和数据结构

```java
public final class JsonUtils {
    public static String toJson(Object value);
    public static <T> T fromJson(String json, Class<T> type);
    public static <T> T fromJson(String json, TypeReference<T> type);
    public static JsonNode toTree(Object value);
    public static <T> T convert(Object source, Class<T> type);
    public static <T> T deepCopy(Object source, Class<T> type);
    public static <T> T deepCopy(Object source, TypeReference<T> type);
}
```

不公开可变全局 ObjectMapper。确需 mapper 的 Adapter 使用 `JsonUtils.mapperCopy()` 返回 copy，不能修改 shared instance。

### 关键实现逻辑

- 时区不在 common 强制 Asia/Shanghai；`Instant` 是绝对时间，platform 展示时处理时区。协议已有时间格式若不同由显式注解保持。
- `toTree(Object)` 与当前仅接收 JSON string 的实现区分；另提供 `parseTree(String)`，避免方法语义混淆。
- 深拷贝前检查目标类型，不能把接口/抽象类型无类型信息地复制。
- common mapper 不注册 Spring AI Message/ChatResponse、MyBatis entity 或 platform module。

### 异常处理

- 统一抛 `JsonEncodingException`/`JsonDecodingException`，保留 cause；错误信息写目标类，不回显完整敏感 JSON。
- 输入 null 的约定：`toJson(null)` 返回 null；`fromJson(null/blank)` 返回 null；`deepCopy(null)` 返回 null，并用测试固定。
- 不可序列化 metadata 在进入持久化前失败，不能默默转 `toString()`。

### 测试方案

- generic List/Map、record、enum、Instant、协议多态、SessionSnapshot round-trip。
- save/load 双向别名测试所需的深层 Map/List/ToolCall 数据。
- mapperCopy 修改配置不影响全局 mapper。
- 源码扫描 common 无 Spring AI module；全项目最终无 Fastjson。

### 架构符合性

JSON 基础能力保持框架中立，Spring AI 和数据库特殊类型各自在所属 Adapter 转为 common 后再序列化，符合依赖倒置和单一序列化栈目标。
