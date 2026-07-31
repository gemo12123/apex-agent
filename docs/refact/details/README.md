# Apex Agent 后端重构详细设计索引

> 状态：待评审、可进入实现准备
> 日期：2026-07-31
> 范围：`apex-agent` 后端；不修改 `apex-frontend/src`
> 任务覆盖：50/50

## 1. 文档目的

本目录把原始需求、总体设计、目标架构和 `docs/refact/tasks/` 的 50 个子任务展开为可编码的详细设计。每个任务均明确实现目标、涉及模块/类、核心流程、接口与数据结构、关键实现逻辑、异常处理、测试方案和架构符合性。

本目录不是对上游文档的改写摘要。设计细节同时基于当前源码主链进行推演，重点解决以下实现阶段必然遇到的问题：

- 单模块源码如何在父 POM 切换时保持旧链路可运行。
- 中立模型怎样无损承载 Spring AI 的消息、ToolCall 和流响应。
- 构造定义、运行状态、恢复快照和请求级对象如何避免互相污染。
- Hook 分型结果怎样原子应用，人工介入怎样恢复到同一个 ToolCall。
- 两个 Repository 不提供跨库事务时，怎样通过稳定 ID 和幂等写降低部分提交风险。
- 同步 HTTP 409、异步执行、请求级 END 和 session lease 怎样由一个所有权模型收口。
- MCP、SubAgent、Skill、Memory 如何在不反向污染 core 的前提下迁移。

## 2. 输入与优先级

实现时按以下优先级判断：

1. 外部 HTTP/SSE 兼容以 `docs/spec/消息标准.md` 和已冻结 Golden File 为准。
2. 重构目标以总体设计和目标架构中“已确认设计决策”为准。
3. 具体编码契约以本目录详细设计为准。
4. 当前行为核对以 `apex-agent/src/` 和现有测试为准。
5. `docs/superpowers/` 与 `.worktrees/` 仅是历史记录，不作为实现依据。

若本目录与已确认设计决策冲突，必须先更新本目录，不得在代码中静默选择另一套语义。

## 3. 实施假设

- Java 版本为 JDK 25，构建工具为 Maven。
- 最终 artifact 坐标为 `org.gemo.apex:apex-agent-{module}:1.0-SNAPSHOT`。
- 包根按模块隔离：`org.gemo.apex.protocol`、`common`、`extension`、`core`、`kit`、`runtime`、`platform`、`memory`。
- common 时间统一使用 `Instant`；platform 用 PostgreSQL `TIMESTAMPTZ` 保存，协议字段不受此内部选择影响。
- 集合跨模块时使用不可变副本；动态工具参数和扩展 metadata 才允许使用 JSON Map。
- 定义和快照 schema 首版固定为字符串 `1.0.0`；不实现升级链，但遇到非 `1.0.0` 必须显式拒绝，不能误读。
- runtime 默认 session lease 是立即失败的进程内互斥，不排队；platform 本期只允许单实例。
- 两个 Repository 之间不增加事务端口；每条消息、ToolResult 和压缩操作必须有稳定幂等键，以便部分提交后安全重试或明确失败。

## 4. 当前源码事实校准

2026-07-31 核对结果：

- `apex-agent/src/main/java` 有 248 个 Java 文件，`src/test/java` 有 44 个 Java 文件。
- 当前主链仍是 `ChatController -> ChatService -> SuperAgentCoordinator -> SuperAgentFactory -> SuperAgentSessionService -> SuperAgent`。
- `SuperAgentContext` 仍直接持有 Spring AI Message、ToolCallback、Memory、Plan、Skill 和 `SseEmitter`。
- `ToolCallProcessor` 与 `SubAgentToolCallback` 仍有 Fastjson import。
- 当前 `apex-agent/pom.xml` 仍启用 MySQL 驱动、注释 PostgreSQL 驱动；`application.yml` 已填写 PostgreSQL URL；仓库没有 `application-dev.yml`。
- 因此任务 README 中“POM 已存在 PostgreSQL 驱动未提交修改”和参考文档中“dev profile 覆盖为 MySQL”的描述都不是当前工作区事实。PLAT-03A 必须重新以源码为准处理，不能假定已有可保留的未提交 POM 修改。
- `application.yml` 含机器绝对路径和默认启用的 Memory/Skill Learning 配置；目标 platform 配置必须删除这些默认绑定，外部路径只能作为部署者自有配置。

## 5. 文档清单

| 文档 | 覆盖任务 | 主要内容 |
| --- | --- | --- |
| [00-跨模块契约](00-跨模块契约.md) | 全部 | 包结构、核心接口、快照、状态机、异常和持久化顺序 |
| [01-工程基线与模块骨架详细设计](01-工程基线与模块骨架详细设计.md) | FND-01～03B | Golden File、legacy、父 POM、架构守卫 |
| [02-protocol模块详细设计](02-protocol模块详细设计.md) | PRO-01～02 | 协议 DTO、Jackson 多态和精确 JSON |
| [03-common模块详细设计](03-common模块详细设计.md) | COM-01～04 | 中立领域模型、Hook 结果、快照和 JsonUtils |
| [04-core-extension模块详细设计](04-core-extension模块详细设计.md) | EXT-01～03 | 纯接口端口和类型元数据 |
| [05-core模块详细设计](05-core模块详细设计.md) | CORE-01～07C | 定义构造、生命周期、ReAct、压缩、工具和恢复 |
| [06-kit模块详细设计](06-kit模块详细设计.md) | KIT-01～03 | ask_human、确认、截断和组合器 |
| [07-runtime模块详细设计](07-runtime模块详细设计.md) | RUN-01～08 | Builder、适配器、存储、lease、Skill、MCP、SubAgent |
| [08-platform模块详细设计](08-platform模块详细设计.md) | PLAT-01～04 | Spring Boot、HTTP/SSE、PostgreSQL 和切换验收 |
| [09-memory模块详细设计](09-memory模块详细设计.md) | MEM-01～03 | 长期 Memory 与 Skill Learning 封存 |
| [10-清理与整体验收详细设计](10-清理与整体验收详细设计.md) | CLEAN-01～03 | 删除旧链路、命名收口和交付证据 |
| [11-冲突风险与待确认项](11-冲突风险与待确认项.md) | 全部 | 上游冲突、补充决策、阻塞条件和缓解措施 |

## 6. 开发顺序与提交边界

每个子任务应独立形成可回滚提交，顺序沿用任务 README 的波次，但增加以下约束：

1. FND-01 先创建 Golden File 和缺口特征测试，不修改目标行为。
2. FND-02 只移动旧代码和创建构建骨架，不在同一提交重写业务逻辑。
3. PRO/COM/EXT 先冻结公共契约；下游不得复制同义 DTO。
4. core 每个任务只用 Fake 端口完成，不能等待 Spring、MCP 或 PostgreSQL 才可测试。
5. runtime 完成后先证明无 Spring IoC 可执行，再进入 platform。
6. platform 切换必须同时通过 HTTP/SSE、恢复、409、数据库和前端零修改门槛。
7. CLEAN 只删除已被新链路覆盖且有测试证据的旧代码。

共享文件的唯一所有者沿用任务 README；对 `apex-agent/pom.xml`、公共 record 和数据库 migration 的并行修改必须串行合并。

## 7. 单任务完成定义

单个任务只有同时满足以下条件才算完成：

- 生产类型和包位置与本文一致。
- 对外接口及不变量有单元或契约测试。
- 失败分支有明确状态、异常类型和副作用断言。
- 没有引入反向模块依赖。
- 迁移了对应旧测试，或记录旧测试被哪个新测试替代。
- 实际运行了任务文档列出的最小验证命令。
- 若改变配置、启动或协议，已同步当前态文档。

## 8. 已确认的补充决策

2026-07-31 已确认并写入全部分册：

- MCP/SubAgent 不可用时禁止新活动绑定；已有绑定仅保留只读历史并从模型与执行路径移除，既有 ToolCall/ToolResult 保留展示但不可执行。
- 请求级 Publisher 已绑定后，core 同步构造/恢复准备失败由 platform 返回仅含一个既有 END 的 SSE；参数错误仍为 400，session busy 仍为 409 且无 END。
- 只有 AGENT_BUILD 可以修改 Agent 定义；其他生命周期只能修改各自结果族允许的运行态。

其余仍待确认的条目见 [冲突、风险与待确认项](11-冲突风险与待确认项.md)。
