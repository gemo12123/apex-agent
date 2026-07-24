# 执行轨迹抽屉设置 Popover 设计

日期：2026-07-09  
状态：待评审

## 1. 背景

当前 `apex-frontend` 工作台的信息分布如下：

- 左侧栏承载 `新建会话`、`Agent` 选择、历史占位与 `用户 ID` 设置
- 中间主区承载欢迎态、消息流与人机交互卡片
- 右侧执行轨迹抽屉承载计划、调用与产物时间线

这套布局存在两个问题：

- 会话级设置分散在左侧栏，不属于当前用户关注的“线程与执行上下文”
- `Agent` 与 `用户 ID` 在对话进行过程中仍可见且可改，和当前产品预期不一致

用户已确认本轮目标为：

- 将 `Agent` 选择与 `用户 ID` 设置从现有左侧栏移除
- 在右侧执行轨迹抽屉底部新增一个 `设置` 按钮
- 点击后以贴边 `popover` 方式展开设置面板
- 会话开始后仍保留按钮与弹出效果，但字段置灰、不可修改
- 只有欢迎页与新建后的空会话态允许修改

## 2. 目标

- 将会话设置入口收敛到右侧执行轨迹抽屉底部
- 保留对话过程中的可见性，但禁止会话开始后的变更
- 让设置动作与线程上下文更贴近，而不是分散在左侧导航栏
- 保持现有 `selectedAgentKey` 与 `userId` 的数据来源不变
- 尽量将改动限制在前端展示层与页面事件绑定层

## 3. 非目标

- 不新增更多设置项，例如模型、权限、环境或主题
- 不修改 `session store` 的 `selectedAgentKey`、`userId` 存储规则
- 不修改 `sendPrompt`、`answerPrompt`、`submitConfirmation` 的协议行为
- 不引入真正的会话设置中心或通用浮层系统
- 不重做右侧执行轨迹时间线结构

## 4. 已确认决策

- 使用贴着右侧抽屉底部按钮展开的 `popover`，不是居中 `modal`
- 右侧抽屉底部 `设置` 按钮始终保留
- 对话开始后仍可打开 `popover`
- 对话开始后 `Agent` 下拉与 `用户 ID` 输入框置灰，不可交互
- 欢迎页和新建后的空会话态允许编辑
- 设置面板采用显式保存：
  - 可编辑态显示 `取消 / 保存`
  - 不可编辑态只显示关闭动作

## 5. 方案对比

### 5.1 直接内联到 `TimelineDrawer.vue`

把按钮、弹层、表单状态、保存逻辑全部直接写进 `TimelineDrawer.vue`。

优点：

- 文件数量最少
- 上手最快

缺点：

- `TimelineDrawer` 会同时承担时间线渲染、浮层开关和表单编辑
- 后续再增加线程设置时会让抽屉组件膨胀

### 5.2 独立 `TimelineSettingsPopover` 组件

右侧抽屉只负责展示入口和传递状态，表单、局部草稿、禁用态与动作区由独立组件承担。

优点：

- 组件职责清晰
- 后续扩展设置项时边界稳定
- 便于单独测试交互状态

缺点：

- 需要新增组件和测试文件

### 5.3 做成通用 Popover 基础设施

抽出通用浮层、定位和外部点击关闭机制，再让右侧抽屉复用。

优点：

- 复用性高

缺点：

- 本轮需求范围明显不足以支撑这类抽象
- 增加无必要的实现复杂度

### 5.4 选定方案

采用 **独立 `TimelineSettingsPopover` 组件**。

## 6. 交互设计

### 6.1 入口位置

`设置` 按钮位于右侧执行轨迹抽屉最底部，和上方时间线内容形成固定分区：

- 上部：可滚动时间线列表
- 下部：固定设置入口

这样即使时间线很长，设置入口仍然稳定可见。

### 6.2 Popover 行为

- 点击 `设置` 按钮打开 `popover`
- 再次点击 `设置` 按钮关闭 `popover`
- 点击抽屉内部但面板外部区域时关闭 `popover`
- 点击 `取消`、`关闭`、保存成功后关闭 `popover`
- 抽屉被关闭时，`popover` 也一并重置关闭

`popover` 位置固定在抽屉底部按钮上方，宽度不超过抽屉内部可用宽度。

### 6.3 字段与状态

面板包含两项：

- `智能体`
  - 使用 `select`
  - 数据源继续来自 `agents`
- `用户 ID`
  - 使用单行 `input`
  - 草稿值保存前不直接写回全局状态

可编辑态：

- `hasStarted = false`
- 两个控件可编辑
- 显示 `取消 / 保存`

不可编辑态：

- `hasStarted = true`
- 两个控件置灰并阻止交互
- 显示一行提示：`对话开始后不可修改智能体和用户 ID`
- 仅提供 `关闭`

### 6.4 生效时机

本轮采用显式保存：

- 修改 `智能体` 或 `用户 ID` 时仅更新 `popover` 内部草稿
- 点击 `保存` 后，才调用：
  - `update:selectedAgentKey`
  - `update:userId`
- 点击 `取消` 时丢弃草稿，恢复为当前全局值

这可以避免用户在欢迎态误切换或输入半成品时立即污染全局状态。

## 7. 组件与文件影响

## 7.1 `apex-frontend/src/features/workspace/components/WorkspaceSidebar.vue`

需要移除：

- 左侧栏中的 `Agent` 选择区域
- 左侧栏底部的 `用户 ID` 展开入口与输入框

保留：

- `新建会话`
- 历史占位区

组件职责收敛为纯导航与历史入口。

## 7.2 `apex-frontend/src/features/workspace/components/TimelineDrawer.vue`

需要新增：

- 底部固定操作区
- `设置` 按钮
- `popover` 打开/关闭状态控制
- 与设置组件的事件绑定

同时保持原有时间线渲染逻辑不变。

## 7.3 `apex-frontend/src/features/workspace/components/TimelineSettingsPopover.vue`

新增独立组件，负责：

- 渲染 `智能体` 与 `用户 ID` 字段
- 管理本地草稿
- 根据 `disabled` 或 `editable` 状态切换 UI
- 触发 `save` / `cancel` / `close`

该组件不直接接触 store，只通过 props 和 emits 工作。

## 7.4 `apex-frontend/src/features/workspace/pages/WorkspacePage.vue`

需要补充：

- 将 `hasStarted` 传给 `TimelineDrawer`
- 将 `agents`、`selectedAgentKey`、`userId` 传给 `TimelineDrawer`
- 将 `setSelectedAgent` 与 `setUserId` 从左侧栏事件迁移到右侧抽屉设置保存事件
- 在新建会话时继续保留当前“重置会话但不重置设置”的行为

## 8. 数据流与状态边界

本轮不改变现有全局状态结构，只调整数据进入点。

保持不变：

- `selectedAgentKey` 仍是当前有效智能体来源
- `userId` 仍通过现有逻辑写入 `localStorage`
- `hasStarted` 仍是欢迎态 / 已开始会话态判断源

新增的仅是展示层草稿边界：

- 打开 `popover` 时，用当前全局值初始化本地草稿
- 保存前，草稿不写回 store
- 保存后，按当前既有 setter 写回
- 关闭或取消后，丢弃草稿

## 9. 错误处理与风险

### 9.1 主要风险

- 右侧抽屉底部固定区与滚动时间线区如果处理不当，可能导致遮挡或高度计算错误
- `popover` 关闭时机如果处理不稳，可能出现点击内部控件时被误关闭
- 草稿状态如果没有在 `open` / `close` 时正确重置，可能残留旧值

### 9.2 行为回归风险

必须确保：

- 欢迎态仍可修改智能体与用户 ID
- 一旦发送首条消息，字段立即变为不可编辑
- 新建会话后重新进入空会话态时，字段恢复可编辑
- 对话过程中无法通过任何旧入口修改设置

## 10. 验收标准

满足以下条件即视为达成：

- 左侧栏不再显示 `Agent` 选择和 `用户 ID` 设置
- 右侧执行轨迹抽屉底部始终存在 `设置` 按钮
- 点击按钮会打开贴边 `popover`
- 欢迎态与新建空会话态下，可在 `popover` 中修改并保存设置
- 已开始会话态下，仍可打开 `popover`，但两个字段都置灰
- 已开始会话态下，界面有清晰不可修改提示
- 现有时间线浏览、导出与抽屉关闭行为无回归

## 11. 验证方式

### 11.1 自动化验证

至少补充以下测试：

- `WorkspaceSidebar` 不再渲染 `Agent` 和 `用户 ID` 控件
- `TimelineDrawer` 可打开和关闭设置 `popover`
- `TimelineSettingsPopover` 在 `hasStarted = false` 时允许编辑并触发保存
- `TimelineSettingsPopover` 在 `hasStarted = true` 时字段禁用并显示提示
- `WorkspacePage` 将保存事件正确写回 store

### 11.2 手工验收

重点检查：

- 抽屉滚动很长时，底部设置按钮是否仍固定可见
- `popover` 是否贴边且不溢出抽屉
- 欢迎态修改设置后，首轮发送是否使用更新后的值
- 发送第一条消息后，再打开设置面板是否正确置灰
- 点击 `新建会话` 后设置面板是否恢复可编辑

## 12. 实施边界

允许修改的重点文件：

- `apex-frontend/src/features/workspace/components/WorkspaceSidebar.vue`
- `apex-frontend/src/features/workspace/components/TimelineDrawer.vue`
- `apex-frontend/src/features/workspace/components/TimelineSettingsPopover.vue`
- `apex-frontend/src/features/workspace/pages/WorkspacePage.vue`
- 相关组件测试文件

原则上不应触碰：

- `stores/session/reducer.ts`
- SSE 协议类型
- 后端接口语义
- 与本轮需求无关的页面骨架和视觉系统
