# FND-01 重构前基线报告

> 记录日期：2026-08-01
> 基线提交：`bb27ba9dd120ce728bb2de171c47fb04357483e1`
> 环境：Windows 10 amd64、Oracle JDK 25.0.1、Apache Maven 3.8.1、UTF-8

## 1. 测试基线

在模块迁移前执行：

```text
mvn -f apex-agent/pom.xml test
Tests run: 137, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

迁入 `legacy` 并增加协议、依赖/API 基线测试后执行：

```text
mvn -f apex-agent/pom.xml test
legacy: Tests run: 145, Failures: 0, Errors: 0, Skipped: 0
architecture-tests: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

迁移前共有 248 个生产 Java 文件、44 个测试 Java 文件。整体迁移未修改生产源码；新增 2 个基线测试类后，`legacy` 共有 46 个测试 Java 文件。

## 2. 六类核心场景

可机器读取的映射位于 `apex-agent/legacy/src/test/resources/golden/scenarios/baseline-manifest.json`。

| 场景 | 基线测试 | 目标处置 |
| --- | --- | --- |
| 普通无工具 Turn | `SuperAgentTest#runShouldCompleteAndPersistWhenLoopEndsWithoutToolCalls` | 迁入唯一 ReAct 主循环 |
| 多 Iteration 工具循环 | `SuperAgentTest#runShouldContinueAfterIllegalToolInterception` | 保留多 Iteration 语义 |
| 多 ToolCall 前序成功、后序挂起 | `ToolCallProcessorTest#processShouldAppendCompletedResponsesBeforeLaterToolSuspends` | 保留已完成 ToolResult 和恢复位置 |
| `ask_human` 挂起 | `ToolCallProcessorTest#processShouldSuspendWhenAskHumanIsRequested` | 保留线协议和恢复语义 |
| 工具确认挂起与恢复 | `HumanInLoopResumerTest#resumeShouldExecutePendingToolAfterApprovalWithoutReTriggeringSameHook` | 保留确认 ID、决策和可编辑参数语义 |
| 已执行 PRE Hook 不重复 | 同上 | 迁移为稳定 PRE_TOOL_CALL Hook ID |

`StageToolResolverTest` 中的 PlanExecutor 行为标记为 `LEGACY_ONLY`，只用于迁移期回归，不属于目标态验收，按 CLEAN-01 删除。

## 3. 协议 Golden File

已冻结以下精确 JSON：

- `STREAM_CONTENT`、`ASK_HUMAN`、`TOOL_CONFIRMATION`、`END`
- 兼容保留 DTO：`PLAN_DECLARED`、`PLAN_CHANGE`、`TASK_THINK_DECLARED`、`TASK_THINK_CHANGE`

`LegacyProtocolGoldenTest` 对字段名、空数组、事件类型、未知字段兼容和 `AgentMessage` 多态反序列化进行断言；同时固定：

- `context.mode` 为 `react`
- ReAct 消息不输出 `stage_id`
- END 原始载荷精确为 `{"event_type":"END"}`

现存限制：`TOOL_CONFIRMATION` 的非空 `display_fields` / `editable_fields` 能精确序列化，但旧嵌套类型没有 Jackson 无参构造器，无法完整反序列化。基线测试使用空嵌套数组验证消息多态分派；PRO-02 迁移协议 DTO 时需决定并修复该既有兼容缺口，FND-01 不改旧生产协议类。

## 4. Spring AI 依赖与 API 基线

原始证据：

- `fnd-01-spring-dependency-tree.txt`：Spring AI、Spring Framework 相关 verbose dependency tree
- `fnd-01-legacy-full-dependency-tree.txt`：legacy 完整 verbose dependency tree

生成命令：

```text
mvn -f apex-agent/pom.xml dependency:tree -Dverbose -Dincludes=org.springframework.ai:*,com.alibaba.cloud.ai:*,org.springframework:*
mvn -f apex-agent/pom.xml -pl legacy dependency:tree -Dverbose
```

关键解析事实：

| 类型/依赖 | 声明或传递版本 | Maven 解析版本 | 实际加载 jar |
| --- | --- | --- | --- |
| `ChatModel`、`Message`、`ChatResponse`、`ToolCall`、`ToolResponse`、`ChatOptions` | Spring AI 1.1.0、1.1.2、2.0.0-M1 混用 | 关键模型类为 2.0.0-M1 | `spring-ai-model-2.0.0-M1.jar` |
| `ReactAgent` | Spring AI Alibaba Agent Framework 1.1.0.0-RC2 | 1.1.0.0-RC2 | `spring-ai-alibaba-agent-framework-1.1.0.0-RC2.jar` |
| `ApplicationContext` | Spring Framework 6.1.1、6.2.6、6.2.12、6.2.14、7.0.1 混用 | 6.2.6 | `spring-context-6.2.6.jar` |

由 `SpringAiApiBaselineTest` 固定的 RUN-02 关键签名：

- `ChatModel.call(Prompt): ChatResponse`
- `ChatModel.getDefaultOptions(): ChatOptions`
- `Message.getText(): String`
- `AssistantMessage.ToolCall` 为 4 字段 record
- `ToolResponseMessage.ToolResponse` 为 3 字段 record
- `ChatResponse` 存在单参数构造器
- `ChatOptions.copy()` 返回 `ChatOptions` 可赋值类型

当前多版本组合没有在 FND-02 中主动升级。父 POM 启用 dependency convergence，并仅对原始证据中出现的精确坐标登记 `owner=RUN-02`、`expires-at-task=RUN-02` 的临时例外。完整 legacy 传递冲突另以精确坐标登记至 CLEAN-02，不关闭全局规则。

## 5. 已登记的现状差异与风险

- 任务文档声称开始前存在 PostgreSQL 驱动的局部未提交修改，但实际初始 `git status --short` 未显示该修改；本轮没有臆造或代替 PLAT-03A 调整数据库驱动。
- 默认 `dev` profile 仍使用 MySQL 且 memory 默认关闭，PostgreSQL/pgvector 代码并非默认运行链路；本轮只迁移 legacy，未改变配置语义。
- Spring AI 与 Spring Framework 多版本冲突属于已确认基线，必须由 RUN-02 基于 Adapter 实际契约收敛。
- legacy 继续承担 Spring Boot repackage，只是迁移期豁免；platform 接管后由 CLEAN-02 删除 legacy，最终 repackage 唯一归属 platform。
