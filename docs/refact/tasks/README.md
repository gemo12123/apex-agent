# Apex Agent 后端模块化重构任务列表

> 状态：待实施
> 拆分日期：2026-07-31
> 范围：仅 `apex-agent` 后端；`apex-frontend` 只做兼容验证，不修改源码
> 输入：[原始需求](../原始需求.md)、[模块化重构设计](../specs/2026-07-28-apex-agent-backend-refactoring-design.md)、[目标架构](../2026-07-30-apex-agent-backend-architecture.md)

## 1. 当前基线

- 当前后端仍是单 Maven 模块，包含 248 个生产 Java 文件、44 个测试 Java 文件；八个目标子模块目录尚不存在。
- 当前主实现仍使用 `SuperAgent`、执行模式、PlanExecutor、Spring 容器 Hook 解析、上下文内 `SseEmitter`，并存在 Fastjson 调用。
- 设计文档记录的重构前基线为 137 个后端测试全部通过；本轮拆分未重新执行全量测试，实施前由 `FND-01` 复核。
- 工作区已有用户修改：`apex-agent/pom.xml` 正在把 MySQL 驱动切换为 PostgreSQL 驱动。该改动尚未形成多模块构建、数据库 schema 或 Repository 实现，不能视为 `platform` 持久化任务已完成。
- 原有单文件任务拆分文档当前处于删除状态；本目录不恢复或覆盖该文件。

## 2. 任务文档

评审修订后共 50 个可独立验收的子任务；四个原 Epic 已拆分为更小的实现单元。

| 顺序 | 文档 | 责任边界 | 主要前置 |
| --- | --- | --- | --- |
| 0 | [工程基线与模块骨架](00-工程基线与模块骨架.md) | 特征基线、临时 legacy、父 POM、八模块骨架、分阶段依赖守卫 | 无 |
| 1 | [protocol 模块](01-protocol模块.md) | HTTP/SSE/远程 SubAgent 线协议 DTO | FND-01、FND-02 |
| 2 | [common 模块](02-common模块.md) | 中立领域模型、快照、Hook 结果、JsonUtils | protocol |
| 3 | [core-extension 模块](03-core-extension模块.md) | 仅接口的扩展端口 | common |
| 4 | [core 模块](04-core模块.md) | 定义构造、生命周期、ReAct、工具、压缩、恢复 | common、core-extension |
| 5 | [kit 模块](05-kit模块.md) | `ask_human`、工具确认、截断等通用扩展 | core-extension；部分能力与 core 联调 |
| 6 | [runtime 模块](06-runtime模块.md) | Builder、默认实现、Spring AI 适配、内存存储、Skill、MCP、SubAgent、lease | core、kit |
| 7 | [platform 模块](07-platform模块.md) | Spring Boot、HTTP/SSE、配置、用户上下文、PostgreSQL | runtime |
| 8 | [memory 模块](08-memory模块.md) | 长期记忆、搜索、Skill Learning 独立封存 | common、core-extension |
| 9 | [清理与整体验收](09-清理与整体验收.md) | 删除 PlanExecutor/legacy、重命名、文档与全量验收 | 全部模块 |

## 3. 推荐实施波次

```text
波次 A0：FND-01

波次 A1：FND-02
  -> FND-03A

波次 A2：
  -> PRO-01/02
  -> COM-01/02/03/04
  -> EXT-01/02
  -> EXT-03

波次 B：CORE-01/02/03/04
  -> CORE-05A
  -> CORE-05B/05C
  -> CORE-06
  -> KIT-01/02/03
  -> CORE-07A
  -> CORE-07B
  -> CORE-07C

波次 C：RUN-01
  -> RUN-02/03/04A/04B
  -> RUN-04C
  -> RUN-05/06/07
  -> RUN-08

波次 D：PLAT-01/02/03A
  -> PLAT-03B
  -> PLAT-03C
  -> PLAT-03D
  -> PLAT-04
  || MEM-01/02/03

波次 E：CLEAN-01
  -> CLEAN-02
  -> FND-03B
  -> CLEAN-03
```

说明：

- `||` 表示在依赖已满足且不修改同一迁移文件时可以并行。
- core 与 kit 对人工介入能力存在协作，但职责不重叠：kit 只产生标准 Hook 结果和工具结果，core 独占挂起/恢复状态机。
- `apex-agent/pom.xml` 由 `FND-02` 改为父聚合 POM；同一任务先把原根源码、测试和资源整体迁入临时 `legacy`，旧链路不会退出构建。
- 迁移期只允许 `legacy -> 目标模块`，禁止 `目标模块 -> legacy`。旧源码在对应能力迁走前继续由 legacy 承载，PlanExecutor/legacy 模块最终在 CLEAN 阶段删除。

### 3.1 波次切换门槛

| 门槛 | 必须满足的证据 | 默认运行链路 |
| --- | --- | --- |
| G0：允许切父 POM | FND-01 基线测试与协议 Golden File 通过 | 原根单模块 |
| G1：父 POM 切换完成 | 父 reactor 测试通过；`legacy` 基线测试通过；旧 Spring Boot 入口可启动；目标模块不依赖 legacy | legacy |
| G2：基础契约完成 | PRO/COM/EXT 专项测试通过；legacy 基线继续通过 | legacy |
| G3：core/kit 完成 | core Fake 端口测试覆盖 ReAct、压缩、工具、挂起与五分支恢复；legacy 基线继续通过 | legacy |
| G4：runtime 完成 | runtime-only 无 Spring IoC 执行、lease、Once Publisher、Skill/MCP/SubAgent 测试通过；legacy 基线继续通过 | legacy |
| G5：platform 接管 | PLAT-04 的 HTTP/SSE Golden File、NEW/HUMAN_RESPONSE、409、PostgreSQL 重启恢复和前端零修改验证通过 | 新 platform；legacy 仅作回归对照 |
| G6：删除 legacy | 无目标模块/测试依赖 legacy；CLEAN-01/02 完成；FND-03B 全部规则通过 | 新 platform，且只有八个目标模块 |

## 4. 跨模块共享边界与合并顺序

| 共享对象或文件 | 唯一责任任务 | 下游任务 | 合并顺序 |
| --- | --- | --- | --- |
| `apex-agent/pom.xml`、临时 `legacy`、父级依赖管理、模块清单 | FND-02；删除由 CLEAN-02 | 全部模块 | FND-02 先迁旧源码再切父 POM；后续 reactor 变更串行合并；G5 前 legacy 保持可运行 |
| protocol DTO 与 JSON 字段 | PRO-01/02 | core、runtime、platform | Golden File 冻结后下游适配 |
| common 领域模型、快照、Hook 结果 record | COM-01/02/03 | core-extension、core、runtime、platform、memory | common 契约先合并；下游不得复制同义 DTO |
| core-extension 接口 | EXT-01/02 | core、kit、runtime、platform、memory | 接口先冻结；ToolExecutionObserver 由 EXT-01 定义，core 绑定，runtime 工具消费 |
| AgentDefinition 校验规则 | CORE-01 | runtime Builder、platform Provider | core 先提供唯一校验器；下游不得复制规则 |
| 工具执行期事件 | EXT-01、CORE-06 | RUN-07、RUN-04C | core 创建请求级 observer 并校验 allowlist；SubAgent 只经 observer 透传 INVOCATION；工具不能发布 END |
| 人工介入状态机 | CORE-07A～07C | KIT-01/02、RUN-04C、PLAT-02/03D | kit 先提供结果语义，core 分挂起、校验、五分支实现，下游再接入 |
| END 幂等 | RUN-04C | CORE-04、PLAT-02 | core 只请求发布；runtime Once 包装独占幂等；platform 只调用取消兜底 |
| session execution lease | RUN-04B/04C | PLAT-02 | runtime 是唯一正确性来源；platform 不维护第二套锁表 |
| Repository 提交顺序 | CORE-03/05C/06/07A | PLAT-03C | core 定义调用顺序；各 Adapter 保证单次操作；本期不提供跨 Repository 原子事务 |
| PostgreSQL schema 与 Repository | PLAT-03A/03B | PLAT-03C/03D、MEM-03 | schema、Adapter、顺序、重启测试依次合并；memory 不复用核心会话表 |
| 旧 PlanExecutor/SuperAgent 文件 | CLEAN-01/02 | 所有迁移任务 | 新链路通过后最后删除 |

## 5. 覆盖完整性检查

| 设计主题 | 覆盖任务 |
| --- | --- |
| 迁移期 legacy、八模块 Maven 结构与切换门槛 | FND-02、FND-03A、EXT-03、FND-03B、CLEAN-02/03 |
| 线协议兼容与 Golden File | FND-01、PRO-01/02、PLAT-04 |
| common 中立模型与 Jackson | COM-01/02/03/04 |
| 扩展接口 | EXT-01/02 |
| AgentDefinition、AGENT_BUILD、校验与冻结 | CORE-01 |
| 11 个生命周期点、分型结果与错误策略 | COM-02、EXT-02、CORE-02 |
| Session/Turn/Iteration | COM-01/03、CORE-03 |
| 唯一 ReAct 循环、模型流与压缩门 | CORE-05A/05B/05C |
| 工具三层状态、多 ToolCall、执行期事件 observer | EXT-01、CORE-06、RUN-07 |
| 统一人工介入与 HUMAN_RESPONSE | CORE-07A/07B/07C、KIT-01/02 |
| runtime Builder、Spring AI、内存存储、execution、事件与 lease | RUN-01/02/03/04A/04B/04C |
| 普通 Skill、MCP、HTTP SubAgent | RUN-05/06/07 |
| Spring Boot、HTTP/SSE、用户上下文、Agent 列表 | PLAT-01/02 |
| PostgreSQL schema、Repository、提交顺序与重启恢复 | PLAT-03A/03B/03C/03D |
| Memory/Skill Learning 封存 | MEM-01/02/03 |
| PlanExecutor 删除、ApexAgent 重命名、文档与全量验收 | CLEAN-01/02/03 |

检查结论：

- 原始需求中的八个模块、唯一 ReAct、Agent 配置、工具动态启用、消息出口和新增生命周期均有对应任务。
- 设计文档 0～8 阶段均有落点；每个行为任务都有单元、契约、集成或构建类客观验收项。
- 同一逻辑不存在两个实现责任人；共同修改点均在上一节指定唯一责任和合并顺序。

## 6. 设计缺失或需确认事项

以下事项在输入文档中没有形成可直接编码的唯一结论。任务文档仅标记，不补充关键决策：

| 编号 | 问题 | 影响任务 | 处理要求 |
| --- | --- | --- | --- |
| Q-01 | Turn、Iteration、Session 在模型异常、Hook fail-fast、工具异常、最大 Iteration 超限时的精确终态及错误事件映射未完整列出 | CORE-03、CORE-05A/05B、PLAT-04 | 实施前以当前兼容行为和负责人确认形成状态转换表 |
| Q-02 | `AgentDefinitionProvider` 示例只有按 `agentKey` 加载方法，但 Agent 列表要求从 Provider 读取元数据；缺少列表/枚举端口签名及“不加载完整定义”的约束 | EXT-01、RUN-01、PLAT-01 | 冻结接口前确认元数据枚举契约 |
| Q-03 | File Provider 支持的文件格式、资源发现规则、热加载/缓存语义，以及现有多 Agent 配置到单一完整配置源的精确迁移映射未定义 | RUN-01、PLAT-01 | 不得自行保留旧的全局 + workspace 字段级叠加；先补配置映射决策 |
| Q-04 | MCP/SubAgent 初始化失败支持 fail-fast 或受控降级，但默认策略、配置粒度和降级后的可观测行为未定义 | RUN-06、RUN-07、RUN-08 | Builder API 冻结前确认 |
| Q-05 | 快照要求带版本并使用版本化 Adapter，但首版版本号、支持跨度和遇到未知版本时的行为未定义 | COM-03、PLAT-03B/03D、CLEAN-03 | schema/DTO 冻结前确认；不得静默按当前版本读取 |
| Q-06 | 工具确认拒绝、工具禁用、END_TURN 补齐结果等“标准 ToolResult”的精确 code/message/payload 未给出 | CORE-06、CORE-07C、KIT-02、PRO-02 | 优先从现有行为基线提取；若现状与设计不一致则提交确认 |

这些问题不改变模块边界；未受影响的基础迁移可以先行。受影响任务的对应验收项在决策形成后才能最终签收。
