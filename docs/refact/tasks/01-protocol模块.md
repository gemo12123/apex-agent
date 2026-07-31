# protocol 模块任务

> 模块职责：维护前端、platform、core 事件工厂和远程 SubAgent 共用的线协议
> 当前总体进度：未开始；协议类型仍位于单模块并依赖部分运行上下文

## PRO-01 提取并净化公共协议 DTO

- **任务名称**：迁移 HTTP/SSE 与远程调用公共协议。
- **任务目标**：形成不依赖 core、Spring AI、Servlet、SSE 和数据库实现的独立 `apex-agent-protocol`。
- **当前进度**：未开始。`message/*`、`ChatRequest` 和事件常量仍在单模块；`ToolConfirmationMessage` 仍含上下文构造逻辑。
- **设计依据**：设计文档第 5.1、13、17 节；架构文档第 5.1、13 节。
- **涉及范围**：`ChatRequest`、`RequestType`、`AgentEventType`、`AgentMessage` 及所有具体消息、工具确认展示/编辑/选项 DTO、协议常量。
- **前置依赖**：FND-01、FND-02。
- **具体执行内容**：
  1. 搬迁全部对外可见请求和事件 DTO，保持现有包外协议行为。
  2. 保留 `PLAN_*`、`TASK_THINK_*`、`STREAM_THINK` DTO，但不提供默认生产逻辑。
  3. 移除 `ToolConfirmationMessage.from(context, ...)` 等运行上下文依赖，构造职责交给 core 事件工厂。
  4. 保持 snake_case 字段、Jackson 多态标识、默认值与空字段行为。
  5. 为远程 SubAgent 的消息反序列化保留完整事件类型。
- **预期产出**：可独立编译和发布的 protocol artifact，以及迁移后的协议单元测试。
- **验收标准**：
  - protocol 模块独立 `test` 通过。
  - 字节码/源码检查确认不引用 `ApexAgentContext`、HookContext、Spring AI、`SseEmitter`、Servlet 或数据库类型。
  - 所有当前协议类型均有明确迁移或保留映射，无遗漏事件。
- **限制条件或注意事项**：不得删除兼容 DTO；不得把 `"react"` 抽象为 core 执行模式；不得修改 HTTP 路径、Header、字段名或事件名。

## PRO-02 固化序列化与协议兼容契约

- **任务名称**：建立 protocol 精确 JSON 契约测试。
- **任务目标**：确保模块化和 Jackson 统一后，现有前端无需修改即可继续消费消息。
- **当前进度**：未开始，依赖 FND-01 产出的 Golden File。
- **设计依据**：设计文档第 13.2、21.7、22 节；架构文档第 13、18.4 节。
- **涉及范围**：protocol Jackson 注解/配置、协议测试资源、FND-01 Golden File。
- **前置依赖**：PRO-01、FND-01。
- **具体执行内容**：
  1. 对 `STREAM_CONTENT`、`ASK_HUMAN`、`TOOL_CONFIRMATION`、`END` 做双向精确 JSON 测试。
  2. 对兼容保留的 `PLAN_*`、`TASK_THINK_*`、`STREAM_THINK` 做序列化测试。
  3. 验证所有运行事件的 `context.mode` 兼容值、`content_id`、`tool_call_id`、confirmation/tool/invocation 标识。
  4. 固定 END 不增加 `execution_status` 或其他字段。
  5. 固定工具确认拒绝结果“用户拒绝执行”和 END_TURN 补齐结果“达到最大轮次，强制结束”；两者不增加自定义 code 或 payload。
- **预期产出**：protocol Golden File 测试集和协议兼容报告。
- **验收标准**：
  - 迁移前后 Golden File 字节级或规范化 JSON 结构完全一致。
  - `END` 精确序列化为 `{"event_type":"END"}`。
  - 测试明确区分“DTO 可序列化”和“默认链路会生产该事件”。
- **限制条件或注意事项**：若现有 Golden File 与已确认设计冲突，不可自行选择一方；登记差异并确认。模型可见 ToolResult 属于 common 对话模型，不新增 SSE 事件类型。
