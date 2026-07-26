# Apex Agent Coding Agent 工作指南

本文件适用于整个仓库。目标是让 Coding Agent 先获得完成任务所需的最小上下文，再按需读取更细的说明，避免把历史设计稿、部署示例和当前实现混为一谈。

## 渐进式加载

1. 任何任务先只读本文件，并用 `rg` 定位相关代码。
2. 确认任务类型后，从“开始任务前”的路由中只读与当前任务匹配的 `docs/reference/` 专题。
3. 只有需要项目背景、启动方式、完整执行流、消息协议或交互规范时，才读取 `docs/` 下对应的当前态文档。
4. 仅当专题文件明确引用，或任务涉及对应历史方案时，才读取 `docs/superpowers/`。其中内容是设计与实施记录，不是当前行为的最终依据。
5. 文档与代码冲突时，以当前源码、测试和构建配置为准；修复代码后同步更新相关文档。

## 仓库地图

- `apex-agent/`：JDK 25、Spring Boot、Spring AI 后端；包含 Agent 执行、工具、Hook、记忆和 SSE 接口。
- `apex-frontend/`：Vue 3、TypeScript、Pinia、Vite 工作台；消费 SSE 并维护会话视图状态。
- `docs/`：当前项目说明、开发导航和专题参考。
- `docs/superpowers/`：历史设计、计划与示例，只用于追溯背景。
- `front_outdate/`：旧前端，不属于当前产品链路；除非任务明确要求，否则不要修改。
- `.worktrees/`：历史分支残留的 worktree，内含已废弃的类（如 `SessionExecutionGuard`）和旧版文档。检索代码时必须限定在 `apex-agent/src/` 与 `apex-frontend/src/`，不要把其中内容当作当前实现。
- `target/`、`apex-frontend/.vite/`、`apex-frontend/dist/`、`node_modules/`：生成物或缓存，不作为源代码编辑。

## 全局约束

- 会话、文档和新增注释使用简体中文；文件使用 UTF-8 与 LF。
- 先检查 `git status --short`，保留用户已有修改；不要顺手改动无关文件或生成物。
- 采用最小闭环变更：先找入口和相邻测试，再修改实现，最后运行与风险相称的验证。
- 不凭文件名推测行为。跨层改动必须追踪“后端生产数据 → 前端类型 → reducer/store → 组件”的完整链路。
- 不提交密钥、令牌、个人目录或新的机器绝对路径。`application.yml` 中已有外部 MCP/Skill 绝对路径属于本地示例，不代表仓库自带依赖。
- 不把 `docs/superpowers/` 中的未落地方案写成当前能力，也不要为了匹配旧方案而覆盖新实现。
- 新增行为要补测试；修复缺陷时优先写能复现问题的回归测试。

## 开始任务前

1. 用 `git status --short` 确认工作区状态。
2. 用 `rg --files`、`rg -n` 找到入口、调用方、数据类型和相邻测试。
3. 按任务只加载一个主专题：
   - 后端执行、工具、Hook、记忆、配置：读 [后端开发参考](docs/reference/后端开发.md)。
   - Vue 页面、状态、SSE 消费、交互：读 [前端开发参考](docs/reference/前端开发.md)。
   - API、SSE、人在回路或前后端联动：读 [跨端契约参考](docs/reference/跨端契约.md)，并按需再读前后端专题。
   - 选择测试范围或交付前检查：读 [验证与交付参考](docs/reference/验证与交付.md)。
4. 只有当代码事实仍不足以解释背景时，再按需读取 [项目概览](docs/项目概览.md)、[快速开始](docs/快速开始.md)、[架构与执行流程](docs/架构与执行流程.md)、[消息标准](docs/消息标准.md) 或 [前端交互规范](docs/前端交互规范.md)。

## 核心不变量

- 当前主后端入口是 `POST /api/sse/chat`，Agent 列表入口是 `GET /api/sse/agents`；请求依赖 `X-User-Id`。
- 会话请求以 `sessionId`、`agentKey` 和 `type` 维持身份与恢复语义；不要单边改变字段或默认值。
- SSE 使用 `event_type`、`context`、`messages` 信封。新增或修改事件时，后端消息模型、发送逻辑、前端类型、reducer/store、测试与 `docs/消息标准.md` 必须一起检查。
- `ASK_HUMAN` 与 `TOOL_CONFIRMATION` 会把执行切到人在回路状态；恢复请求使用 `HUMAN_RESPONSE`，不能按普通新消息处理。
- `react` 与 `plan-executor` 的工具可见性不同；涉及计划工具时必须检查 `StageToolResolver` 和 `ToolInterceptor`。
- 当前前端源目录是 `apex-frontend/src/`；协议状态集中在 `types/apex.ts`、`stores/session/reducer.ts` 和 `stores/session/store.ts`。
- 后端当前发送的 `END` 不携带 `execution_status`，前端终止判断实际走缺失字段的兼容分支；`TASK_THINK_CHANGE` 是 plan-executor 模式的主文本通道，但前端 reducer 尚未处理。改动这两处契约前先读 [跨端契约参考](docs/reference/跨端契约.md)。
- 后端默认激活 `dev` profile（`application-dev.yml`）：数据源为 MySQL 且 `apex.memory.enabled: false`；PostgreSQL/pgvector 相关能力默认不生效。

## 常用命令

在仓库根目录执行：

```bash
mvn -f apex-agent/pom.xml test
mvn -f apex-agent/pom.xml -Dtest=ClassName test
npm --prefix apex-frontend run test:run
npm --prefix apex-frontend run typecheck
npm --prefix apex-frontend run build
```

仓库没有统一的根构建，也没有前端 `lint` 脚本。不要声称运行了不存在的检查。更细的测试选择见 [验证与交付参考](docs/reference/验证与交付.md)。

## 完成标准

- 改动范围与用户请求一致，没有覆盖无关工作区修改。
- 新行为和边界条件有对应测试，相关模块的测试已通过。
- 跨端字段、事件、状态和恢复载荷保持一致。
- 配置、启动方式或对外契约发生变化时，已更新当前态文档。
- 最终说明列出修改内容、实际运行的验证和未验证项；不要把未运行的测试描述为通过。
