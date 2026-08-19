# kit 模块任务

> 模块职责：提供不依赖 Spring 容器的基础工具、Hook 和匹配器
> 当前总体进度：已完成（2026-08-02）；KIT-01～KIT-03 已通过 19 项纯 Java 专项测试、依赖分析与 artifact 边界扫描，可供 CORE-07A～07C 联调

## KIT-01 迁移 ask_human 工具与人工提问 Hook

- **任务名称**：把 `ask_human` 迁移为普通 AgentTool + PRE_TOOL_CALL Hook。
- **当前进度**：已完成（2026-08-02）。已提供固定名 `ask_human` 的普通 `AgentTool`、参数解析与 QUESTION 介入 Hook；首次调用只返回标准介入请求，typed `QuestionSubmission` 恢复误重入时继续，真实工具输出按问题 index 稳定排序的 answers JSON。
- **任务目标**：让首次调用通过 Hook 请求 QUESTION 介入，恢复后真实工具读取 humanResponse 并返回普通 ToolResult。
- **设计依据**：设计文档第 5.5、10.3、10.5 节；架构文档第 5.5、8.5 节。
- **涉及范围**：AskHumanTool、AskHumanInterventionHook、HumanInterventionRequest 构造、ToolExecutionContext 使用。
- **前置依赖**：EXT-01/02、COM-02/03；与 CORE-07A/07C 联调。
- **具体执行内容**：
  1. 实现标准 `ask_human` 工具定义。
  2. 首次 PRE_TOOL_CALL 返回 RequestHumanIntervention(QUESTION)。
  3. 恢复时因 Hook ID 已记录而跳过该 Hook，真实工具从上下文读取用户回复。
  4. 工具本身不访问 SSE、SessionRepository 或 core 实现对象。
  5. 覆盖首次挂起、恢复执行、缺失回复和重复激活路径。
- **预期产出**：kit 中的 ask_human 工具、Hook 和单元/联调测试。
- **验收标准**：
  - 首次调用不执行真实工具并产生 QUESTION 请求。
  - 恢复后真实工具恰好执行一次，ToolResult 内容来自 humanResponse。
  - kit 可在无 Spring 容器环境编译和测试。
- **限制条件或注意事项**：core 不得伪造 ask_human ToolResult；协议消息由 CORE-04 构造；挂起/恢复状态由 CORE-07A～07C 独占。

## KIT-02 迁移工具确认与纯文本截断 Hook

- **任务名称**：迁移 ToolConfirmHook、规格构造和 PlainTextTruncateHook。
- **当前进度**：已完成（2026-08-02）。工具确认使用 core 预分配的 intervention ID 构造标准展示 DTO 和允许编辑键；纯文本截断按 Unicode code point 工作并保留非文本结果，kit 未生成确认拒绝、END_TURN 或取消结果。
- **任务目标**：用标准分型结果表达工具确认与结果截断，不直接操作执行上下文或事件出口。
- **设计依据**：设计文档第 5.5、8.3、10.5 节；架构文档第 5.5、7.2 节。
- **涉及范围**：ToolConfirmHook、确认规格构造器、工具/Hook 匹配器、PlainTextTruncateHook。
- **前置依赖**：COM-02、EXT-02、PRO-01；与 CORE-06、CORE-07A～07C 联调。
- **具体执行内容**：
  1. ToolConfirmHook 在匹配工具时返回 TOOL_CONFIRMATION 类型人工介入请求。
  2. 规格构造器只输出 protocol 展示字段、选项和允许编辑键。
  3. PlainTextTruncateHook 通过 PostToolCall 专用结果修改文本，不改变非文本结果。
  4. 参数批准/拒绝的状态机不在 kit 实现；由 CORE-07C 解释。
- **预期产出**：工具确认、截断 Hook、匹配器和行为测试。
- **验收标准**：
  - 匹配/不匹配、Hook order、确认展示载荷和允许编辑字段有确定测试。
  - 截断长度、边界和非文本保持行为有测试。
  - Hook 只返回 common 结果，不引用 core 类、SSE 或 Spring Bean。
- **限制条件或注意事项**：不得在 kit 保存挂起状态；批准只允许修改配置声明的参数。kit 只产生确认请求，不生成拒绝、END_TURN 或取消 ToolResult；固定结果及文案由 CORE-06/07C 独占。

## KIT-03 建立通用组合器并排除计划工具

- **任务名称**：完成 kit 注册内容和模块边界收口。
- **当前进度**：已完成（2026-08-02）。已提供精确名/`*` 匹配辅助和同 descriptor Hook 组合器；kit 直接项目依赖精确为 protocol、common、core-extension，源码与 artifact 不含计划工具、执行模式守卫、Spring/core 实现引用或固定状态机结果工厂。
- **任务目标**：提供可复用 Hook 组合/匹配辅助，同时确保 kit 不包含 PlanExecutor 工具或 core 实现依赖。
- **设计依据**：设计文档第 5.5、17、20 节阶段 4；架构文档第 5.5 节。
- **涉及范围**：Hook 组合器、工具匹配器、kit POM 与架构测试。
- **前置依赖**：KIT-01、KIT-02、EXT-03。
- **具体执行内容**：
  1. 提供不依赖 Spring 的 Hook 组合和工具匹配辅助。
  2. 确保 kit 直接项目依赖精确为 protocol、common、core-extension，不依赖 core/runtime/platform；不允许以传递依赖替代直接声明。
  3. 不迁移 WritePlanTool、UpdatePlanTool 和模式守卫。
  4. 将旧计划工具的实际删除留给 CLEAN-01，避免迁移期提前破坏旧链路。
  5. 增加架构测试，禁止 kit 出现 `StandardToolResultFactory` 或拒绝、强制结束、取消等固定状态机结果文案。
- **预期产出**：完整 kit artifact、架构测试和注册清单。
- **验收标准**：
  - kit 独立测试通过且不依赖 core 具体实现。
  - artifact 中不存在 WritePlanTool、UpdatePlanTool、PlanExecutor/Stage 守卫。
  - runtime 可通过接口注册 kit 工具/Hook，无 Spring ApplicationContext。
  - kit 源码和 artifact 不包含拒绝/强制结束/取消 ToolResult 工厂。
- **限制条件或注意事项**：kit 不拥有工具注册表、Hook 调度器或人工介入状态机；这些分别属于 runtime 与 core。

## 本轮验证与 core 联调契约

- `mvn -q -pl protocol,common,core-extension,legacy -am test`：通过，复核 G2 前置门槛。
- `mvn -q -pl kit -am test`：通过，kit 19 项专项测试全部成功。
- `mvn -q -pl kit -am verify`：通过，包含 kit 打包和 `maven-dependency-plugin:analyze-only`。
- `mvn -pl kit dependency:tree "-Dincludes=org.gemo.apex:*"`：通过，直接项目依赖仅 protocol、common、core-extension；独立执行时因上游 SNAPSHOT POM 未 install 出现元数据警告，不影响 reactor `verify` 与最终依赖树。
- artifact 清单仅包含 `org.gemo.apex.kit` 下 11 个生产 class；自动架构测试扫描编译产物，禁止 core/runtime/platform/Spring 引用、计划工具、`StandardToolResultFactory` 和三段固定状态机结果文案。
- 提供给 CORE-07A～07C 的联调契约：`AskHumanInterventionHook` 首次返回 `RequestHumanIntervention(QuestionInterventionRequest)`，恢复携带任意 typed submission 时不重复挂起；`AskHumanTool` 只接受关联当前 toolCallId 的 `QuestionSubmission` 并返回普通 `ToolResult`；`ToolConfirmHook` 使用 `proposedInterventionId` 和 `invocationId` 返回 `ToolConfirmationInterventionRequest`，允许修改的参数键只来自已校验的 editable-fields；kit 不解释批准/拒绝，不保存挂起状态，也不合成任何状态机 ToolResult。
