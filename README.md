# apex-agent

`apex-agent` 是一个基于 Spring Boot 与 Spring AI Alibaba 的智能体运行时服务。它把多阶段 Agent 执行、MCP 工具接入、SubAgent 协作、Skills 激活，以及会话级 Memory 能力统一到了一个后端项目里，并通过 SSE 向前端持续输出标准化消息。

## 项目特点

- 三阶段执行链路：`THINKING -> MODE_CONFIRMATION -> EXECUTION`
- 两种执行模式：`react` 与 `plan-executor`
- 统一工具体系：内置工具、MCP、SubAgent、Skills
- 记忆系统：会话持久化、摘要压缩、长期记忆抽取、记忆管理接口
- 标准消息协议：面向前端、Hub、父子 Agent 的统一 `event_type` 消息

## 仓库结构

- `apex-agent/`：Spring Boot 主工程与全部 Java 源码
- `docs/`：项目说明、架构文档、快速开始、消息协议
- `README.md`：仓库入口说明

## 文档导航

- [项目概览](docs/项目概览.md)
- [快速开始](docs/快速开始.md)
- [架构与执行流程](docs/架构与执行流程.md)
- [消息标准](docs/消息标准.md)

## 快速启动

1. 准备 `JDK 25` 与 `Maven`。
2. 配置环境变量 `DASHSCOPE_API_KEY`。
3. 根据实际环境调整 `apex-agent/src/main/resources/application.yml` 中的 MCP 与 Skills 路径。
4. 在项目根目录执行：

```bash
cd apex-agent
mvn spring-boot:run
```

服务默认监听 `http://localhost:18083`。

## 对外接口

- `GET /api/sse/agents`：获取可用 Agent 列表
- `POST /api/sse/chat`：以 SSE 形式执行会话
- `GET /api/memory/items`：分页查询记忆
- `PATCH /api/memory/items/{memoryType}/{memoryId}`：更新记忆内容
- `DELETE /api/memory/items/{memoryType}/{memoryId}`：删除记忆
- `DELETE /api/memory/execution-history`：清空执行历史记忆

所有接口都要求请求头带上 `X-User-Id`。

## 配置提醒

- 默认记忆存储是 `in-memory`，切换到 `jdbc` 后需先执行 `apex-agent/src/main/resources/db/memory-schema-postgresql.sql`
- `application.yml` 与 `application-dev.yml` 中的 MCP 和 Skills 路径是示例绝对路径，落地时需要替换为你自己的本地路径
- 默认模型来自 DashScope，当前配置为 `qwen-plus`

