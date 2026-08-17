# apex-agent

`apex-agent` 是一个面向 Agent 工作流的运行时仓库，当前由两个主要子项目组成：

- `apex-agent/`：基于 Spring Boot、Spring AI Alibaba 的后端运行时
- `apex-frontend/`：基于 Vue 3 + Vite 的对话工作台

它的核心目标不是“包一层模型接口”，而是把一次真实的 Agent 执行会话拆成可控的运行时能力：执行模式切换、工具编排、人在回路、工具确认、记忆管理、SSE 事件输出，以及前端会话可视化。

## 当前实现包含什么

- 单一内部阶段：当前主执行链路统一进入 `EXECUTION`
- 双执行模式：`react` 与 `plan-executor`
- 四类工具来源：内置工具、MCP、SubAgent、Skills
- Hook 体系：支持工具执行前确认/改参、执行后结果加工
- 记忆系统：会话持久化、摘要压缩、长期记忆抽取、执行历史与经验记忆
- 会话检索：内置 `session_search`，面向历史对话与摘要检索
- Skill 经验学习：记录 `activate_skill` 使用片段并按计划任务生成经验记忆
- SSE 协议：后端输出结构化事件，前端将其还原为消息、阶段、调用和产物

## 仓库结构

- `apex-agent/`：Java 后端源码、资源、测试
- `apex-frontend/`：Vue 前端源码、测试、Vite 配置
- `docs/`：当前态文档；`overview/` 项目背景与启动、`spec/` 协议与交互规范、`reference/` 按任务路由的专题参考、`superpowers/` 历史设计记录
- `front_outdate/`：旧前端遗留目录，不是当前主前端
- `want_learn/`：学习/试验目录，不属于主运行链路

## 后端关键能力

### 1. Agent 定义加载

后端通过 `AgentDefinitionClasspathYmlLoader` 合并三层来源：

- `application.yml` 中的 `apex.global.agents.*`
- `src/main/resources/agents/<agentKey>/config.yml`
- workspace prompt 缺失时的默认 prompt 回退

当前仓库里实际自带的 agent workspace 资源只有：

- `apex-agent/src/main/resources/agents/codex/REACT_PROMPT.md`
- `apex-agent/src/main/resources/agents/deer-flow/REACT_PROMPT.md`
- `apex-agent/src/main/resources/agents/deer-flow/config.yml`

也就是说，`application.yml` 里的 `default_agent`、`meeting_tool`、`contacts_tool` 更接近“可运行示例配置”，并不意味着仓库内已经包含它们对应的完整 workspace 文件或外部依赖。

### 2. 内置工具

当前后端内置了四个核心工具：

- `ask_human`
- `write_plan_tool`
- `update_plan_tool`
- `session_search`

其中：

- `ask_human` 用于挂起会话并等待用户补充输入
- `write_plan_tool` / `update_plan_tool` 用于 `plan-executor` 模式
- `session_search` 用于检索历史对话与摘要

### 3. Hook 与确认机制

当前实现已经支持工具 Hook：

- `pre-tool-call`：可在执行前触发确认、展示字段、允许用户修改参数
- `post-tool-call`：可对工具结果做截断或经验增强

仓库内置了这类能力的真实实现，例如：

- `ToolConfirmHook`
- `PlainTextTruncateHook`
- `JsonTruncateHook`
- `SkillExperienceAugmentHook`
- `SkillUsageRecorderHook`

前端也已经实现了 `TOOL_CONFIRMATION` 的交互与恢复链路。

### 4. 记忆与检索

后端记忆系统包括：

- 会话上下文存储
- 对话摘要压缩
- 用户画像记忆
- 执行历史记忆
- Agent 经验记忆
- 记忆管理 API
- 会话搜索索引与检索

默认存储类型是 `in-memory`。切到 `jdbc` 后会启用 JDBC 版会话/记忆仓储。

## 前端当前形态

`apex-frontend` 不是模板页，而是一个已经接入后端 SSE 协议的工作台，支持：

- 加载 agent 列表
- 发起会话与恢复挂起会话
- 展示正文消息流
- 展示计划阶段、工具调用、结构化产物
- 处理 `ASK_HUMAN`
- 处理 `TOOL_CONFIRMATION`

Vite 开发环境通过代理把 `/apex-api/*` 转发到 `http://localhost:18083/api/*`。

## 快速启动

### 1. 环境准备

- `JDK 25`
- `Maven 3.9+`
- `Node.js 20+`
- `DASHSCOPE_API_KEY`

可选：

- PostgreSQL + `pgvector`，当你要启用 JDBC 记忆与 `session_search` 时使用

### 2. 检查后端配置

主要配置文件：

- `apex-agent/platform/src/main/resources/application.yml`
- `apex-agent/platform/src/main/resources/application-dev.yml`

MCP Client 通过 `spring.ai.mcp.client.*` 配置连接；发现的工具还需在
`apex.platform.agents.*.tools` 中授权给对应 Agent。外部 MCP Server 和 Skill 目录不是仓库内置资源，落地时需要换成实际可用的地址或路径。

### 3. 启动后端

```bash
cd apex-agent
mvn spring-boot:run
```

默认端口：`http://localhost:18083`

### 4. 启动前端

```bash
cd apex-frontend
npm install
npm run dev
```

默认访问：`http://localhost:5173`

## 当前对外接口

### SSE 会话接口

- `GET /api/sse/agents`
- `POST /api/sse/chat`

### 记忆管理接口

- `GET /api/memory/items`
- `PATCH /api/memory/items/{memoryType}/{memoryId}`
- `DELETE /api/memory/items/{memoryType}/{memoryId}`
- `DELETE /api/memory/execution-history`

所有接口都要求请求头包含 `X-User-Id`。

## 已知实现边界

- 仓库当前不自带外部 MCP Server 代码；`spring.ai.mcp.client.*` 需要指向实际可用的外部服务
- 仓库当前不自带 `meeting-skill`、`contacts-skill` 目录；skill 路径是示例外部依赖
- 当前资源目录没有 `default_agent` workspace；`default_agent` 主要依赖 `application.yml` 与默认 prompt 回退
- JDBC 记忆/search 路径的 schema 与实现围绕 PostgreSQL/pgvector 设计，但 `pom.xml` 目前保留的是 `mysql-connector-java`，并将 PostgreSQL 驱动注释掉；如果你要真正启用 JDBC，请先把驱动、URL 和 schema 策略对齐

## 文档导航

- [项目概览](docs/overview/项目概览.md)
- [快速开始](docs/overview/快速开始.md)
- [架构与执行流程](docs/overview/架构与执行流程.md)
- [消息标准](docs/spec/消息标准.md)
- [前端交互规范](docs/spec/前端交互规范.md)
- [前端说明](apex-frontend/README.md)
