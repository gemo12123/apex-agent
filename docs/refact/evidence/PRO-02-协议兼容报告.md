# PRO-02 协议兼容报告

> 记录日期：2026-08-01
> 工作集：PRO-01、PRO-02

## 1. 结论

`apex-agent-protocol` 已独立承载 HTTP 请求、13 类 SSE/远程 SubAgent 事件、人在回路详情以及 Q-14 只读会话状态响应 DTO。既有聊天路径、Header、请求字段和 SSE 事件名未修改；`context.mode="react"` 仍是普通线协议字符串，未被抽象为执行模式。

legacy 中原包 DTO 在迁移期继续保留，新增对照测试使用同一组构造数据及 FND-01 的 8 份 Golden File 验证新旧规范化 JSON 一致。

## 2. Golden File 覆盖

- 默认运行事件：`STREAM_CONTENT`、`ASK_HUMAN`、`TOOL_CONFIRMATION`、`INVOCATION_DECLARED`、`INVOCATION_CHANGE`、`END`。
- 仅兼容 DTO：`STREAM_THINK`、`PLAN_DECLARED`、`PLAN_CHANGE`、`TASK_THINK_DECLARED`、`TASK_THINK_CHANGE`、`ARTIFACT_DECLARED`、`ARTIFACT_CHANGE`。测试只证明可序列化和反序列化，不声明默认链路会生产这些事件。
- Q-14 状态响应：ASK_HUMAN 挂起、TOOL_CONFIRMATION 挂起、COMPLETED 无 pending interaction。

`END` 的原始字符串固定为 `{"event_type":"END"}`，不包含 `execution_status`、code、payload 或其他字段。模型可见 ToolResult 文本不属于 protocol，本工作集未新增对应 SSE 事件。

## 3. 实际验证

```text
mvn -f apex-agent/pom.xml -pl protocol verify
Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
No dependency problems found
BUILD SUCCESS

mvn -f apex-agent/pom.xml -pl protocol,legacy -am test
protocol: Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
legacy: Tests run: 156, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

mvn -f apex-agent/pom.xml test
protocol: Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
legacy: Tests run: 156, Failures: 0, Errors: 0, Skipped: 0
architecture-tests: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 4. 构建兼容说明

Maven Dependency Plugin 3.8.1 自带的 ASM 9.7 无法读取 JDK 25 class major version 69。本工作集仅在 protocol 的插件依赖中覆盖 ASM 9.8，使 `dependency:analyze-only` 可执行；未改变业务运行依赖。

Jackson `annotations/core/databind` 保持 FND-01 已解析的 2.16.1 基线。曾尝试 2.18.3 后触发 legacy `NoSuchMethodError`，已撤销并通过父 reactor 回归，不把 RUN-02 的依赖收敛提前到本工作集。
