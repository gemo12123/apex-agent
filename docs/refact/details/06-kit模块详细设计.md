# kit 模块详细设计

## 模块设计定位

`apex-agent-kit` 提供可被任何 runtime 注册的通用 AgentTool 和 LifecycleHook 实现，直接依赖 protocol、common、core-extension。展示 DTO、common 结果和扩展接口均为源码直接引用，不能依靠传递依赖。它不保存 Session、不发布事件、不解析 Spring Bean，也不拥有恢复状态机。

目标包：`tool`、`hook`、`intervention`、`matcher`。

## KIT-01 迁移 ask_human 工具与人工提问 Hook

### 实现目标

把 ask_human 表达为普通 AgentTool，并用 PRE_TOOL_CALL Hook 在首次到达时请求 QUESTION 人工介入；恢复后 Hook 被跳过，真实工具从 typed ToolExecutionContext 读取答案并返回正常 ToolResult。

### 涉及模块/类

源：`tool/AskHumanTool`、`ToolCallProcessor.processAskHuman`。

目标：`kit.tool.AskHumanTool`、`kit.hook.AskHumanInterventionHook`、`AskHumanArgumentsParser`、`QuestionInterventionFactory`。

### 核心流程

首次：模型调用 ask_human -> PRE Hook匹配 -> 解析问题参数 -> 返回 RequestHumanIntervention(QUESTION) -> core 挂起。

恢复：core 跳过已执行 Hook -> 调用 AskHumanTool -> 工具从 `ToolExecutionContext.humanSubmission` 读取 QuestionSubmission -> 返回关联当前 toolCallId 的 ToolResult。

### 接口和数据结构

工具名固定 `ask_human`。输入 schema 延续当前问题结构：问题列表包含 index、input_type、question、description、options。Hook 只构造 common `QuestionInterventionRequest`，protocol AskHumanMessage 由 core 工厂产生。

答案规范化为稳定 JSON：按问题 index 升序输出 answers；单值保持字符串，多选保持字符串数组。ToolResult.content 是该 JSON 字符串，便于模型无歧义读取。

### 关键实现逻辑

- Hook descriptor 固定 PRE_TOOL_CALL + PreToolCallContext/Result。
- 只在 `toolCall.name == "ask_human"` 且当前 context 无 humanSubmission 时请求介入；恢复后即使因配置错误再次调用 Hook，也应检测 submission 并 Continue，避免无限挂起。
- 工具本身要求 QuestionSubmission；缺失或类型错误抛 ToolExecutionException，由 core 转结果。
- ToolResult 保留原 toolCallId/name，不直接写 conversation。

### 异常处理

- 模型参数无法解析或问题为空：Hook 返回 BlockTool，并给出模型可见原因，不创建无内容交互。
- 恢复缺答案：工具抛可读错误；是否允许部分答案以前端实际问题 required 语义为准，未标 required 的问题允许缺失。
- kit 不捕获 Repository/Publisher 异常，因为它不接触这些端口。

### 测试方案

- 首次无 submission 返回 QUESTION；真实工具 0 次由 core 联调断言。
- 有 submission 时 Hook Continue，工具返回排序稳定的 answers JSON。
- 单选、多选、自定义值、空可选答案、非法参数。
- 无 Spring ApplicationContext/SSE/core import 架构测试。

### 架构符合性

ask_human 成为普通工具 + Hook 组合，人工状态仍由 core 管理，符合统一 PRE_TOOL_CALL 介入模型。

## KIT-02 迁移工具确认与纯文本截断 Hook

### 实现目标

迁移工具确认 spec 构造、匹配与文本结果截断，用 common 分型结果表达，不直接操作执行上下文或事件出口。

### 涉及模块/类

源：`ToolConfirmHook`、`PlainTextTruncateHook`、`ToolMatcher`、ToolConfirmationSpec/DisplayField/EditableField。

目标：`kit.hook.ToolConfirmHook`、`PlainTextTruncateHook`、`kit.matcher.GlobToolMatcher`、`kit.intervention.ToolConfirmationSpecFactory`。

### 核心流程

- ToolConfirmHook 根据 Binding tools/options 匹配，生成 confirmationId/invocation展示信息，返回 RequestHumanIntervention(TOOL_CONFIRMATION)。
- PlainTextTruncateHook 在 POST_TOOL_CALL 接收最终 ToolResult；纯文本超过上限时返回 ToolResultPatch，其他类型/未超限 Continue。

### 接口和数据结构

确认 options 支持：title、description、risk-level、tool-display-name、confirm-label、deny-label、display-fields、editable-fields。editable 由 editable-fields 非空计算，调用方不能单独配置冲突值。risk 默认 MEDIUM。

kit 只产生 `ToolConfirmationInterventionRequest` 和截断 Patch，不产生“用户拒绝执行”“达到最大轮次，强制结束”或“请求已取消，工具未执行完成”结果。这些结果分别由 core 的确认恢复、ReAct 终止和请求取消分支决定，统一经 CORE-06 `ToolResultFactory` 构造。

### 关键实现逻辑

- Hook 不生成/发送 protocol message，只返回 common intervention request，其中展示字段使用 protocol DTO。
- invocationId 是当前 ToolCall 的稳定ID，confirmationId直接使用 PreToolCallContext 为本次Binding预分配的 `proposedInterventionId`；kit不调用UUID，也不持有IdGenerator。
- Glob matcher 支持 `*` 和精确名，匹配规则一次编译，非法 pattern 在注册/定义校验阶段失败。
- 截断按 Unicode code point 而非 UTF-16 char，避免切断代理对；追加清晰截断标识时必须计入 maxLength。
- 非文本结果通过 messageType/metadata 判断，不对 JSON/二进制引用做字符串截断。

### 异常处理

- editable field重复、指向不存在参数或展示配置缺字段在定义校验时失败。
- maxLength <=0 构造失败。
- spec 构造普通异常属于 Hook 执行异常会被 core warn 后跳过，但静态非法配置应在请求构造前发现。

### 测试方案

- 工具精确/*/不匹配、order 由 core联调。
- 完整确认 JSON 交给 CORE-04/PRO-02 Golden File。
- editable 推导、允许键、默认 risk/labels。
- 截断 0/边界/超长/emoji/非文本。
- 联调断言确认拒绝只返回介入请求；恢复后的固定 ToolResult 由 CORE-06/07C 测试覆盖，kit 测试不复制文案断言。

### 架构符合性

kit 只实现可复用 Hook/工具规则，所有状态和发布仍由 core，协议 DTO 保持底层依赖方向。

## KIT-03 建立通用组合器并排除计划工具

### 实现目标

提供无 Spring 的 Hook 组合和匹配辅助，形成 kit 最终注册清单，并保证 artifact 不含计划工具、Stage 守卫或 core 实现依赖。

### 涉及模块/类

目标：`CompositeLifecycleHook`、`ToolMatcher` 实现、kit POM/架构测试。旧 `WritePlanTool`、`UpdatePlanTool` 不迁移。

### 核心流程

1. 汇总 KIT-01/02 的公开注册名和 descriptor。
2. Composite 只组合相同 HookPoint/Context/Result 的 Hook；顺序在组合器内部按显式列表，不读取 Spring order。
3. runtime Builder 按稳定注册名注册这些实例。
4. 构建 artifact 后扫描禁用类型和依赖。

### 接口和数据结构

推荐默认注册名：`askHumanInterventionHook`、`toolConfirmHook`、`plainTextTruncateHook`；工具名 `ask_human`。稳定名写入默认 AgentDefinition，不能使用类简单名自动推导。

Composite 对任一子 Hook 返回终止动作时停止后续子 Hook并返回该结果；普通异常不在组合器捕获，交 core 统一策略，避免双重吞异常。

### 关键实现逻辑

- 组合器不是 LifecycleDispatcher，不解析 Binding、不应用状态。
- kit POM 直接依赖 protocol、common、core-extension；不直接声明 core/runtime/platform。
- 旧计划工具留在 legacy 直至 CLEAN-01，kit artifact 从第一天就不存在这些类型。
- kit 不声明 `StandardToolResultFactory` 或任何固定状态机结果文案；架构测试禁止出现该类型和三段文案，防止与 core 形成双重所有权。

### 异常处理

- descriptor 不同的 Hook 组合在构造时拒绝。
- 重复稳定注册名由 runtime Builder 拒绝，不在 kit 静态全局注册。

### 测试方案

- Composite Continue/终止/异常传播。
- artifact 扫描无 WritePlanTool、UpdatePlanTool、PlanExecutor、StageTool。
- dependency tree 无 core/runtime/platform/Spring context；源码/artifact 无 StandardToolResultFactory。
- runtime Fake registry 能注册并按 descriptor 解析。

### 架构符合性

kit 保持“接口的通用实现库”而非运行时容器，支持外部 runtime 复用且不会形成 core -> kit 反向依赖。
