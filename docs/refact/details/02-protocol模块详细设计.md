# protocol 模块详细设计

## 模块设计定位

`apex-agent-protocol` 只表达线上 HTTP/SSE 数据，不承担消息创建、业务校验或运行状态。它同时服务 platform HTTP 边界、core 事件工厂和 runtime HTTP SubAgent 反序列化，因此必须可以独立发布。

目标包结构：

```text
org.gemo.apex.protocol
├── request
│   ├── ChatRequest
│   ├── RequestType
│   └── SessionStateView
├── event
│   ├── AgentMessage
│   ├── AgentEventType
│   └── *Message
├── event.detail
│   ├── AskHumanDetail
│   ├── ToolConfirmationDetail
│   ├── ToolConfirmationDisplayField
│   └── ToolConfirmationEditableField
└── json
    └── ProtocolObjectMapperFactory（仅测试/独立使用需要时）
```

## PRO-01 提取并净化公共协议 DTO

### 实现目标

搬迁全部当前请求和事件 DTO，保持 JSON 外形不变，同时去掉 `ToolConfirmationMessage.from(SuperAgentContext, ToolCall, ...)` 等对运行上下文、Hook 类型和 Spring AI 的反向依赖。

### 涉及模块/类

源类型：

- `domain/dto/ChatRequest`、`constant/RequestType`。
- `constant/AgentEventType`、`constant/MessageFields`。
- `message/AgentMessage` 与 13 个具体消息类型。
- `hook/tool/ToolConfirmationDisplayField`、`ToolConfirmationEditableField` 中纯展示字段迁入 protocol；`ToolConfirmationSpec` 仍是 kit/common 的业务结构，不进入 protocol。

目标类型：`org.gemo.apex.protocol.request.*`、`org.gemo.apex.protocol.event.*`、`event.detail.*`。

### 核心流程

1. 从 FND-01 manifest 生成事件迁移清单，逐个标记目标类。
2. 原样搬迁 Jackson 字段注解和多态 type name。
3. 把嵌套 detail DTO 提升为独立 public record 或不可变 POJO，便于 core 直接构造。
4. 删除 DTO 的 static `from(context, ...)`；core 的 `AgentEventFactory` 负责把中立数据映射到 protocol。
5. legacy 临时改为依赖 protocol，并通过 Adapter 保留旧 package 引用；Adapter 只存在 legacy，目标模块不反向兼容旧包。

### 接口和数据结构

`ChatRequest` 的 JSON 字段保持 `query`、`sessionId`、`agentKey`、`type`、`humanResponse`。为保持现有 camelCase HTTP 请求，不施加全局 snake_case 策略；SSE 明确 snake_case 的字段继续用 `@JsonProperty`。

新增只读响应 DTO：

```java
public record SessionStateView(
        String sessionId,
        String agentKey,
        String executionStatus,
        AgentMessage pendingInteraction) {}
```

`pendingInteraction` 只允许 ASK_HUMAN/TOOL_CONFIRMATION 两个现有子类；protocol 只表达外形，HITL 与空值不变量由 platform 映射器校验。该 DTO 是新增 GET 的响应 data，不进入 SSE 信封。

`AgentMessage` 继续使用：

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
              include = JsonTypeInfo.As.PROPERTY,
              property = "event_type")
@JsonSubTypes({ ... })
public abstract class AgentMessage {
    @JsonIgnore private String eventType;
    @JsonProperty("context") private Map<String, Object> context;
}
```

为避免破坏现有 JSON，首轮迁移保留 class + Lombok 构造形态，不在同一任务机械改成 sealed record。构造后由构造器复制 context/messages/detail 集合；`getContext()` 不返回可修改内部 Map。

事件全集：STREAM_THINK、STREAM_CONTENT、PLAN_DECLARED、PLAN_CHANGE、INVOCATION_DECLARED、INVOCATION_CHANGE、TASK_THINK_DECLARED、TASK_THINK_CHANGE、ARTIFACT_DECLARED、ARTIFACT_CHANGE、END、ASK_HUMAN、TOOL_CONFIRMATION。

### 关键实现逻辑

- `AgentEventType` 保留编译期字符串常量，因为 `@JsonSubTypes.Type(name=...)` 不能调用 enum 方法。
- `EndMessage` 的 null `context/messages` 依赖 `NON_NULL` 才能得到精确载荷；protocol 测试必须显式使用与平台相同的 Jackson inclusion，不能依赖调用方默认 mapper。
- `ToolConfirmationDetail.display_fields/editable_fields` 改为 protocol 自有 DTO；不得再 import `org.gemo.apex.hook.*`。
- PLAN/TASK/STREAM_THINK DTO 继续反序列化，但文档和测试标注 `compatibilityOnly=true`，不添加运行生产者。
- protocol 不定义内部 `HumanInterventionRequest`、ToolResult 或 Session 状态。

### 异常处理

- 反序列化未知 event_type 时抛 Jackson 明确异常，由 SubAgent SSE parser 转换为远端消息异常；protocol 不吞掉或路由到 NoOp。
- 兼容事件出现未知字段继续忽略，保持当前 `FAIL_ON_UNKNOWN_PROPERTIES=false` 的接收兼容。
- 必填列表为 null 的输入可以反序列化，但业务使用前由 platform/core 校验；protocol 不引入业务校验框架。

### 测试方案

- `ProtocolDependencyTest`：源码/字节码无 core、extension、Spring AI、Servlet、SSE、ORM 引用。
- `AgentMessagePolymorphismTest`：13 种事件按 event_type 双向反序列化。
- `ToolConfirmationDtoTest`：完整字段、空展示列表、editable 推导结果由上层设置后的 JSON。
- `ChatRequestCompatibilityTest`：默认 type、默认 agentKey 和 HUMAN_RESPONSE Map。
- `SessionStateViewTest`：HITL 两种多态交互、非 HITL 空 interaction、REST camelCase 外层、pending 消息 snake_case 内层和 round-trip。
- `ProtocolInventoryTest`：当前事件常量与 `@JsonSubTypes` 一一对应。

### 架构符合性

DTO 与构造逻辑分离后，protocol 成为依赖图最底层；core 和远程 SubAgent 可以共享同一线协议而不引入 Web 或执行上下文。

## PRO-02 固化序列化与协议兼容契约

### 实现目标

将 FND-01 Golden File 迁入 protocol 契约测试，锁定字段名、null 省略、多态 event_type、数组顺序和 END 精确载荷，证明 Jackson 统一不会改变现有前端输入。

### 涉及模块/类

- protocol 全部消息类、测试 mapper。
- `src/test/resources/golden/protocol/*.json`。
- FND-01 捕获的原始样本与 `docs/spec/消息标准.md`。

### 核心流程

1. 对每个 Golden File 反序列化为 AgentMessage 并检查具体子类。
2. 再序列化为 JsonNode，与规范化样本结构比较。
3. 对 END 同时做 JsonNode 和原始字符串精确比较。
4. 对运行事件断言 context.mode 是 react、无 stage_id；兼容 DTO 样本可保留历史 mode/stage 字段但不纳入默认运行集合。
5. 生成协议兼容报告，列出“可序列化”和“默认会生产”两个维度。

### 接口和数据结构

Golden File命名固定：

```text
stream-content.json
ask-human.json
tool-confirmation.json
end.json
session-state-ask-human.json
session-state-tool-confirmation.json
session-state-completed.json
plan-declared.compat.json
plan-change.compat.json
task-think-declared.compat.json
task-think-change.compat.json
stream-think.compat.json
invocation-declared.json
invocation-change.json
artifact-declared.compat.json
artifact-change.compat.json
```

模型可见的“用户拒绝执行”“达到最大轮次，强制结束”“请求已取消，工具未执行完成”是 common ToolResult，不是 SSE DTO。protocol 这里只断言没有新增对应事件或 code/payload；文本、关联 ID 与空 metadata 的主断言归 core 唯一 `ToolResultFactory`（CORE-06/CORE-07C）。

### 关键实现逻辑

- JSON 对象字段顺序一般不构成协议，但 END 是明确的原始字符串契约；其余使用结构比较，避免 mapper 属性排序造成无意义失败。
- protocol 测试只使用本模块 test scope 的最小 Jackson `ObjectMapper` 验证 DTO 注解和 Golden File，不引用 common `JsonUtils`。使用产品 JsonUtils 的消费者 round-trip 迁到 COM-04，保持依赖方向始终为 common→protocol。
- `content_id`、`tool_call_id`、`confirmation_id`、`invocation_id` 做值保留断言，不仅验证字段存在。
- `context.mode="react"` 由 core 事件工厂负责设置；protocol 测试使用固定对象验证序列化，不把 mode 设计成 enum。

### 异常处理

- FND-01 样本与当前消息标准冲突时，保留两份差异并标记阻塞，不能自动更新 Golden File。
- 规范化 round-trip 丢字段时先判断是否 null 省略导致；只有当前协议明确省略的 null 可接受。
- 未知字段接收兼容不等于发送端可以新增字段；发送 Golden File 不允许额外属性。

### 测试方案

- 参数化执行全部 Golden File 的 serialize/deserialize 测试。
- 专测 END：空构造、context=null、messages=null 均输出精确一字段。
- 专测 ToolConfirmation 所有展示/编辑字段 snake_case。
- 用远程 SSE 的 `data:` 包装样本验证 runtime parser 去掉 SSE 前缀后可直接交给 protocol mapper。
- legacy 与新 protocol 对同一构造数据生成 JSON 对比，直至 platform 切换。
- protocol 通过 `maven-jar-plugin:test-jar` 附加只读 Golden File/测试夹具供 common 的 test scope 消费；execution 显式绑定 `process-test-classes`，保证根工程执行 `mvn test` 时附件已经可解析，而不是等到 package。该附件不得引用 common 测试类。

### 架构符合性

精确契约测试把“既有聊天/SSE 零破坏”转成自动证据，同时允许新增只读状态 DTO，并让兼容 DTO 留存而不把已删除的 PlanExecutor 重新带入 core。
