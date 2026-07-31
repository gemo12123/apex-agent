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
| 3 | [core-extension 模块](03-core-extension模块.md) | 仅接口的扩展端口 | protocol、common |
| 4 | [core 模块](04-core模块.md) | 定义构造、生命周期、ReAct、工具、压缩、恢复 | protocol、common、core-extension |
| 5 | [kit 模块](05-kit模块.md) | `ask_human`、工具确认、截断等通用扩展 | protocol、common、core-extension；部分能力与 core 联调 |
| 6 | [runtime 模块](06-runtime模块.md) | Builder、默认实现、Spring AI 适配、内存存储、Skill、MCP、SubAgent、lease | protocol、common、core-extension、core、kit |
| 7 | [platform 模块](07-platform模块.md) | Spring Boot、HTTP/SSE、配置、用户上下文、PostgreSQL | protocol、common、core-extension、runtime |
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
| 人工介入状态机 | CORE-07A～07C | KIT-01/02、RUN-04C、PLAT-02/03D | kit 先提供介入请求语义，core 分挂起、校验、五分支实现，下游再接入 |
| 状态机合成 ToolResult | CORE-06/07C | PRO-02、KIT-02 | core 唯一工厂持有拒绝/强制结束/取消文案与关联逻辑；protocol 只守线协议，kit 不生成固定结果 |
| END 幂等 | RUN-04C | CORE-04、PLAT-02 | core 只请求发布；runtime Once 包装独占幂等；platform 只调用取消兜底 |
| session execution lease | RUN-04B/04C | PLAT-02 | runtime 是唯一正确性来源；platform 不维护第二套锁表 |
| 请求级取消 | COM-01、RUN-04A/04C | EXT-01、CORE-03/04/05B/06、RUN-02/06/07/08、PLAT-02 | runtime source 发命令；observer/context 共享 token；默认adapter主动取消；finally 独占 END/lease 收口 |
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
| 11 个生命周期点、分型结果与异常处理 | COM-02、EXT-02、CORE-02 |
| Session/Turn/Iteration | COM-01/03、CORE-03 |
| 唯一 ReAct 循环、模型流与压缩门 | CORE-05A/05B/05C |
| 工具三层状态、多 ToolCall、执行期事件 observer | EXT-01、CORE-06、RUN-07 |
| 统一人工介入与 HUMAN_RESPONSE | CORE-07A/07B/07C、KIT-01/02 |
| runtime Builder、Spring AI、内存存储、execution、请求级取消、事件与 lease | COM-01、EXT-01、CORE-03/04/05B/06、RUN-01/02/03/04A/04B/04C、PLAT-02 |
| 普通 Skill、MCP、HTTP SubAgent | RUN-05/06/07 |
| Spring Boot、HTTP/SSE、用户上下文、Agent 列表 | PLAT-01/02 |
| PostgreSQL schema、Repository、提交顺序与重启恢复 | PLAT-03A/03B/03C/03D |
| Memory/Skill Learning 封存 | MEM-01/02/03 |
| PlanExecutor 删除、ApexAgent 重命名、文档与全量验收 | CLEAN-01/02/03 |

检查结论：

- 原始需求中的八个模块、唯一 ReAct、Agent 配置、工具动态启用、消息出口和新增生命周期均有对应任务。
- 设计文档 0～8 阶段均有落点；每个行为任务都有单元、契约、集成或构建类客观验收项。
- 同一逻辑不存在两个实现责任人；共同修改点均在上一节指定唯一责任和合并顺序。

## 6. 设计确认结果

Q-01～Q-09 已于 2026-07-31 确认，以下结论是对应任务的实施与验收依据：

| 编号 | 已确认决策 | 影响任务 |
| --- | --- | --- |
| Q-01 | 模型调用异常时立即结束本次执行，当前 Iteration、Turn、Session 记为 `FAILED`，不再执行后续 Hook、工具或模型调用；请求仍通过既有 `END` 收口，不新增错误事件。Hook 执行异常统一记录 warn，丢弃该 Hook 的全部修改后跳过，不提供 `FAIL_FAST` 配置；静态契约非法仍在注册或定义校验阶段失败。工具执行异常转换为当前 ToolCall 的 ToolResult 告诉模型，Turn 继续。最后一个允许的 Iteration 必须提示模型直接输出最终结论且不再调用工具；若仍返回 ToolCall，则按 Q-06 强制结束。 | COM-01/02、CORE-02/03、CORE-05A/05B、CORE-06、PLAT-04 |
| Q-02 | `AgentDefinitionProvider` 同时提供按 `agentKey` 加载完整定义和 `listAgents()` 获取 `List<AgentMetadata>` 的能力；列表接口直接返回轻量元数据，不通过逐个加载完整定义实现。 | EXT-01、RUN-01、PLAT-01 |
| Q-03 | `FileAgentDefinitionProvider` 默认只支持 YAML；资源由调用方显式指定，可来自 classpath 或文件系统，Provider 初始化时加载一次并缓存，不扫描目录、不热加载。本期不设计现有全局/workspace 配置的迁移映射。 | RUN-01、PLAT-01 |
| Q-04 | MCP 或 SubAgent 初始化失败时记录 warn、关闭失败资源并登记不可用状态，健康集成和 runtime 继续启动。不可用工具禁止新活动绑定；新 session 或 AGENT_BUILD 新增该绑定时构造失败。已有 session 的旧绑定转为只读历史记录并从有效定义、`enabledTools` 和 ToolCatalog 移出；既有 ToolCall/ToolResult 保留展示但不可执行，也不自动重新启用。 | COM-03、EXT-01、CORE-01/06/07B、RUN-06/07/08 |
| Q-05 | 首版快照/定义 schema 版本固定为字符串 `1.0.0`，本期只实现该版本的读写与 round-trip；跨版本升级、版本跨度和未知版本处理均不在本期范围，不宣称跨版本兼容。 | COM-03、PLAT-03B/03D、CLEAN-03 |
| Q-06 | 工具确认拒绝映射为 `RETURN_TOOL_RESULT`，模型可见结果文本固定为“用户拒绝执行”。禁用工具不进入模型工具列表，执行前仍做二次校验以阻止伪造或过期调用。`END_TURN` 遇到当前或剩余 ToolCall 时，逐个按原 toolCallId/name 补齐结果文本“达到最大轮次，强制结束”。这两类结果不增加自定义 code 或 payload，并由 core 内部唯一 `ToolResultFactory` 生成；kit 只产生确认请求。 | PRO-02、CORE-06、CORE-07C |
| Q-07 | Header/请求字段错误返回 HTTP 400；session busy 返回 HTTP 409 且不发 END。请求级 Publisher 已绑定、lease 已取得后，core 同步构造或恢复准备失败由 runtime 发布唯一且载荷不变的 END、释放 lease；platform 返回 HTTP 200 的仅 END SSE，不追加错误事件或文本。 | RUN-04C、PLAT-02/04 |
| Q-08 | 只有 AGENT_BUILD 可以通过 `AgentDefinitionOperation` 修改 Agent 定义。其他生命周期不得修改定义或 Hook 链；消息、session `enabledTools`、当前模型/工具/压缩对象仍按各自结果族修改，且属于运行态。 | COM-02、CORE-01/02 |
| Q-09 | 每个 execution 使用唯一请求级取消 token。运行中 `cancel()`/`close()` 只发非阻塞取消命令，不等待、不提前释放 lease；默认模型、HTTP/SubAgent、MCP adapter 必须主动取消底层调用。runtime close 向全部活动 execution 发命令后返回，不设置取消超时或 grace period；最终 END、请求资源和 lease 只由实际执行 finally 收口。若 Assistant ToolCall 已持久化，core 为未完成调用按原ID/name补“请求已取消，工具未执行完成”且 metadata 为空的结果后结束，不运行后续 Hook/真实工具/模型。 | COM-01、EXT-01、CORE-03/04/05B/06、RUN-02/04A/04C/06/07/08、PLAT-02 |

这些确认不改变模块边界；所有原受阻任务均可按上述结论进入实施和最终签收。
