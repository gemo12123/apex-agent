# CLEAN-03 最终交付验收报告

> 日期：2026-08-04
> 结论：CLEAN-03 已完成；除 PostgreSQL/Testcontainers 因环境缺失未实际运行外，其余自动化、架构、前端和静态验收通过。PG 项按本轮用户明确豁免接受，但不记为通过。

## 1. 前置门槛

| 前置项 | 证据 | 结论 |
| --- | --- | --- |
| CLEAN-01/02 | `CLEAN-01-02清理报告.md` | 已完成 |
| FND-03B / G6 | `FND-03B-最终架构验收报告.md` | 已完成 |
| PRO-02 | `PRO-02-协议兼容报告.md` | 已完成 |
| CORE-07C | `04-core模块.md` 与 core 36 项专项测试 | 已完成 |
| RUN-08 | `RUN-08-runtime资源关闭报告.md` 与 runtime 17 项专项测试 | 已完成 |
| PLAT-04 / G5 | HTTP/SSE、状态查询、前端和静态 schema 通过；PG 由用户环境豁免 | 已完成（含未实测项） |
| MEM-03 | `MEM-03-memory构建隔离报告.md` 与最终静态检查 | 已完成 |

## 2. 最终覆盖矩阵

| requirementId | decisionId | taskId | productionTypes | tests | command | result | evidencePath | notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 唯一 ReAct 与生命周期 | Q-01/Q-06 | CORE-01～07C | `ApexAgent`、`LifecycleDispatcher`、`ModelStepExecutor`、`ToolCallCoordinator` | `LifecycleCoverageTest`、`ApexAgentExecutionTest` | core 专项命令 | 36 项通过 | 本报告 §3 | 覆盖 11 生命周期、ReAct、模型、压缩、多工具、取消 |
| 挂起与五分支恢复 | D-05 | CORE-07A～07C | `InterventionSuspender`、`HumanResponseParser`、`ApexAgentFactory` | `HumanInterventionExecutionTest`、`ApexAgentFactoryTest` | core 专项命令 | 通过 | 本报告 §3 | ASK_HUMAN、批准、拒绝、再次挂起、全部继续 |
| runtime-only/lease/Once | D-09 | RUN-04A～04C/RUN-08 | `ApexAgentRuntime`、`ApexAgentExecution`、`OnceAgentEventPublisher` | `RuntimeContractTest`、`RuntimeCancellationIntegrationTest` | runtime 专项命令 | 17 项通过 | 本报告 §3 | close 只发命令，finally 才释放 lease |
| 模型/MCP/HTTP 取消 | R-10/D-09 | RUN-02/06/07/08 | `SpringAiModelGateway`、`McpAgentToolAdapter`、`HttpSubAgentTool` | `RuntimeCancellationIntegrationTest` | runtime 专项命令 | 通过 | 本报告 §3 | 本轮修复模型取消等待与外部异常取消映射 |
| Skill/MCP/SubAgent/资源 | D-02/D-06 | RUN-05～08 | runtime skill/mcp/subagent/resource | 两个 runtime 测试类 | runtime 专项命令 | 通过 | 本报告 §3 | SubAgent HITL 不透传；owned 逆序关闭 |
| HTTP/SSE/400/409/END-only | D-03/D-05 | PLAT-02/04 | controller/service/SSE publisher | platform Web/状态专项测试 | platform 专项命令 | 11 项非 PG 通过 | 本报告 §3 | 只读查询无副作用，两类 HITL 映射通过 |
| PostgreSQL/TEXT/重启恢复 | D-04/R-08 | PLAT-03A～03D | Flyway、Postgres Repository、V1 Adapter | `PostgresRepositoryIntegrationTest` | platform 专项命令 | 未运行：1 项跳过 | 本报告 §4 | 当前环境无 Docker；用户明确豁免，不写通过 |
| memory 归档隔离 | D-07/R-15 | MEM-01～03 | memory POM/MANIFEST/archive | FND-03B 架构测试 + 静态扫描 | `clean verify` + 静态检查 | 通过 | 本报告 §3 | memory 未编译、未测试；0 jar、0 class |
| 前端刷新回显 | D-05/Q-14 | PLAT-04 | API、store、reducer | 前端 13 文件 48 项 | test:run/typecheck/build | 通过 | 本报告 §3 | 两类 HITL、404 清理、5xx 保留均覆盖 |
| 最终架构与依赖 | D-08/R-13/R-21/R-22 | FND-03B/CLEAN-03 | 父 POM 与七代码模块 | 全部架构测试 | 架构专项 + `clean verify` + dependency tree | 通过 | 本报告 §3 | Spring AI 1.1.2 单一解析线，Enforcer 无豁免 |

## 3. 实际运行的验证

| 命令 | 结果 |
| --- | --- |
| `mvn -f apex-agent/pom.xml test` | 成功；本轮修复前基线通过，PG 1 项跳过 |
| core 6 类专项测试命令 | 成功；36 项，0 失败/错误/跳过 |
| runtime 两类专项测试命令 | 成功；17 项，0 失败/错误/跳过 |
| platform schema/Repository/HTTP/SSE/状态/取消专项命令 | 成功；12 项中 11 通过、PG 1 跳过 |
| 全部自动架构测试专项命令 | 成功；protocol/common/extension/core/kit/FND-03B 架构规则通过 |
| `mvn -f apex-agent/pom.xml clean verify` | 成功；38 份报告共 182 项，0 失败、0 错误、1 跳过；七模块依赖分析与 Enforcer 通过 |
| `mvn ... dependency:tree -Dincludes=org.gemo.apex:*,org.springframework.ai:*,com.alibaba.cloud.ai:* -Dverbose` | 成功；项目依赖符合固定图；Spring AI model/commons/template-st 均为 1.1.2 |
| `npm --prefix apex-frontend run test:run` | 成功；13 文件、48 项通过 |
| `npm --prefix apex-frontend run typecheck` | 成功 |
| `npm --prefix apex-frontend run build` | 成功；Vite 138 modules transformed |
| memory 静态检查 | 成功；无 `src/main`/`src/test`，0 jar、0 class，七代码模块标准源码 0 归档引用 |

最终 `clean verify` 前曾因新增测试直接引用 reactive-streams 导致依赖分析/作用域门禁失败；测试改用 Reactor 自身 API 后移除该直接依赖，runtime verify 与最终 clean verify 均重新通过。

## 4. 未验证项与环境豁免

- `PostgresRepositoryIntegrationTest` 使用 Testcontainers；当前环境找不到 Docker，1 项被跳过。
- 因此空 PostgreSQL Flyway、真实长 TEXT、数据库约束/幂等和进程重启恢复未在本轮实际执行。
- 用户明确说明 PG 环境暂不支持并允许默认接受，本轮据此解除 PLAT-04/G5/CLEAN-03 状态阻塞；本报告不把这些项目描述为通过。
- 未进行真实模型密钥、真实 MCP server、真实远程 SubAgent 或浏览器人工烟测；对应逻辑由 Fake/Adapter 自动测试覆盖。

## 5. 默认能力与限制

- 唯一执行循环为 ReAct；PlanExecutor、Stage、计划工具、SuperAgent、legacy 和 Fastjson 已删除。
- platform 默认使用 PostgreSQL 新库和 TEXT `1.0.0` 快照；旧 MySQL 不兼容。
- memory 仅为非编译历史归档；默认没有长期召回、`session_search`、Memory 管理或 Skill Learning。
- runtime 请求级取消会主动传播到 Spring AI、MCP 和 HTTP SubAgent；自定义不合作工具仍可能长期占用资源。
- 远程 SubAgent 不支持嵌套人工介入。
- 本期仅单实例安全，必须 `replicas=1`，升级停旧再启新；共享 PostgreSQL 不提供分布式 lease。

## 6. 发布与回滚

完整步骤见 [部署、发布与回滚](../../overview/部署、发布与回滚.md)。回滚必须把 legacy 版本、旧配置和旧数据库作为一套恢复；禁止新 platform 读取旧 MySQL，也禁止旧版本读取新 PostgreSQL TEXT 快照。
