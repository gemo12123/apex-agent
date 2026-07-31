# platform 模块详细设计

## 模块设计定位

`apex-agent-platform` 是唯一 Spring Boot 可执行应用。它负责配置映射、HTTP/SSE、用户边界、异步派发和 PostgreSQL Adapter，不重新实现 Agent 循环、Hook、工具、恢复、END 幂等或 session lease。

platform POM 直接声明 protocol、common、core-extension、runtime：Web DTO/协议消息、命令对象、`AgentEventPublisher` 实现和 runtime API 都是直接源码引用；不直接依赖 core 或 kit。

目标包：`bootstrap`、`config`、`web`、`web.sse`、`security`、`execution`、`persistence.session`、`persistence.conversation`、`persistence.snapshot`。

## PLAT-01 迁移 Spring Boot 装配、Agent 配置与列表接口

### 实现目标

建立新的 platform 启动模块，把 Spring YAML 一次性映射为完整 AgentDefinition，并用 Provider.listAgents 维持 Agent 列表响应，不保留当前全局 + workspace 字段级叠加。

### 涉及模块/类

源：`ApexApplication`、`ApexGlobalProperties`、`AgentConfig`、`AgentDefinitionClasspathYmlLoader`、`SuperAgentConfiguration`、`ChatController.getAgents`。

目标：`platform.bootstrap.ApexApplication`、`ApexAgentPlatformConfiguration`、`ApexAgentPlatformProperties`、`SpringPropertiesAgentDefinitionProvider`、`AgentController`。

### 核心流程

1. Spring 绑定新的完整配置对象并执行 Bean Validation/自定义 structural validation。
2. Provider 在初始化时把 properties 转为中立 AgentDefinition 缓存。
3. Runtime Builder接收这一个 Provider和 Spring 注册适配器。
4. GET agents 直接 provider.listAgents -> 响应 DTO。

### 接口和数据结构

目标 YAML 每个 Agent 是完整定义：

```yaml
apex:
  platform:
    agents:
      default_agent:
        name: 通用智能体
        description: 通用智能体
        prompt:
          system: classpath:agents/default_agent/REACT_PROMPT.md
        message-compression:
          enabled: true
          trigger-estimated-tokens: 24000
          retain-latest-messages: 20
        tools:
          available: [ask_human]
          default-enabled: [ask_human]
        skills:
          enabled: []
        hooks: {}
```

MCP server、Skill roots 等 runtime 外部资源配置放独立 `apex.platform.integrations`，AgentDefinition 只引用稳定工具/Skill 名。配置中不存在 workspace merge、default-execution-mode、Plan Prompt、learning Hook。

### 关键实现逻辑

- provider 初始化解析 prompt resource为 UTF-8文本；classloader/file资源规则与 RUN-01一致，可复用 runtime resource loader，而非另写优先级。
- properties map key 是权威 agentKey，内部重复 agent-key 字段可取消；如保留则必须一致。
- `listAgents` 使用缓存 metadata，不读取 prompt/MCP/Skill或调用 load。
- Spring Bean Hook/Tool通过显式配置/适配注册到 runtime稳定 name；Bean name不能自动成为恢复 ID。
- 当前 `application.yml` 的机器绝对路径和 Skill Learning默认配置不能迁入目标默认资源；提供环境变量/示例占位。

### 异常处理

- 多定义源、重复 key、资源不存在、properties结构非法在应用启动失败。
- 单个 MCP/SubAgent运行初始化失败由 runtime 登记 availability，不等同于 properties解析失败，也不阻止应用启动；但请求构造含该新绑定的 Agent 时按已确认策略返回仅 END SSE，platform 不在 properties 层静默删除绑定。
- Agent列表 Provider异常映射 500 标准响应，不能逐个跳过隐藏配置问题。

### 测试方案

- `ApplicationContextRunner` 或小型 Spring context装配单一 runtime。
- 完整 YAML、多 agent、冲突源、缺 prompt、无旧字段。
- listAgents 调用 load=0，响应 `code/data/message` 与 agentKey/name。
- 默认配置无机器绝对路径、learning Hook、执行模式。

### 架构符合性

platform 只把 Spring 配置适配为中立 Provider，定义构造/权威校验仍由 core Assembler。

## PLAT-02 接入 HTTP/SSE、用户上下文与异步执行

### 实现目标

保持现有路径、Header 和 ChatRequest，使用 runtime 同步 execution准备实现 409，并让每次 NEW/HUMAN_RESPONSE 拥有独立 emitter/Publisher。

### 涉及模块/类

源：`ChatController`、`ChatService`、`SuperAgentCoordinator`、`UserContextFilter`、`ChatExecutionConfiguration`、`MessageUtils`。

目标：`ChatController`、`ChatService`、`ApexAgentCoordinator`、`SseEmitterAgentEventPublisher`、`UserContextFilter/Holder`、`UserContextTaskDecorator`、异常映射。

### 核心流程

```text
Filter validates X-User-Id
  -> Controller validates ChatRequest shape
  -> create SseEmitter + request Publisher
  -> synchronously runtime.newAgent/resumeAgent
  -> busy: discard emitter, HTTP 409
  -> AgentPreparationException(endPublished=true): complete and return HTTP 200 END-only emitter
  -> success: dispatch execution.run
  -> task rejected: execution.cancelBeforeStart
  -> return emitter
```

每次 HUMAN_RESPONSE重复以上流程，绝不复用挂起前 emitter。

### 接口和数据结构

- NEW：query非空、humanResponse可空。
- HUMAN_RESPONSE：humanResponse非空、query不参与执行。
- Header `X-User-Id` 必填，显式写入 AgentRequest/HumanResponseCommand。
- `SseEmitterAgentEventPublisher.publish` 用 common JsonUtils 序列化并 `emitter.send(serializedString)`；实现请求级 closed 标识。发布 END 只负责 send，不在 `publish` 内调用 emitter.complete；complete 由 execution terminator 在状态已置 TERMINATED 后执行。取消执行由 `ApexAgentExecution` 独占，不在 Publisher 内另建 token。

### 关键实现逻辑

- runtime API在返回前已获取 lease，platform删除 `runningAgents/sessionLocks`。
- SessionBusyException 不发送 END，直接由 `@ExceptionHandler` 映射 409；此时 SSE尚未提交。
- 已确认：`AgentPreparationException(endPublished=true)` 表示 core 同步构造/恢复准备失败且 runtime 已写入唯一精确 END、释放 lease。Coordinator 记录 correlation/phase/cause，调用 emitter.complete 并返回 HTTP 200 `text/event-stream`；不得调用 `completeWithError`、映射 4xx/5xx、发送异常文本或增加第二个 END。
- `endPublished=false` 说明 Publisher 创建/发送本身失败，无法满足 END-only 契约；此时才按平台基础设施异常处理并记录告警，不能返回一个声称已正常收口的空 SSE。
- 取得 execution 后才提交异步任务。TaskRejectedException立即 cancelBeforeStart，Once确保 END一次。
- TaskDecorator捕获请求线程 userId，异步执行前 set，finally clear；core/runtime始终使用命令中的 userId，不读 ThreadLocal。
- Coordinator 用 `AtomicReference<ApexAgentExecution>` 保存句柄。emitter completion/timeout/error 先把 Publisher 标记 closed，再对已绑定 execution 调用 `cancel()`；runtime 返回 execution 后写入引用，并在提交任务前再次检查 Publisher closed，已关闭则调用 `cancel()` 而不提交，闭合“callback 先于句柄绑定”的竞态。
- callback 只发非阻塞取消命令，不能直接释放 lease、发布 END 或等待执行退出；RUNNING 的 lease 仍归 execution finally。底层模型、HTTP/MCP 和工具取消由请求 token 的已注册 command 完成。

### 异常处理

- Header缺失/请求字段非法在同步边界返回 400。
- busy 409；不创建运行任务。
- core 构造/恢复准备失败由上述 END-only 分支返回；SSE 对外不暴露异常类型、message 或 stack trace。
- Sse send失败抛 AgentEventPublishException，background结束并释放 lease。
- task rejection和Publisher错误不能重复 END/complete。
- emitter callback 与 execution 绑定竞态由 closed 二次检查收口；回调本身的 cancel 异常只记录请求标识，不向已关闭连接写消息。
- END send 成功与 emitter complete 分属 Once Publisher/terminator；不得让 Publisher 在 execution 尚为 RUNNING 时 complete 并触发反向 cancel。

### 测试方案

- MockMvc/Controller：路径、media type、Header、NEW/HUMAN_RESPONSE路由。
- 同 session NEW/恢复冲突在 Controller返回前409；不同 session并行。
- 参数错误 400、busy 409 均无 END；core 构造失败和恢复快照校验失败均返回 200，解析出的事件序列严格等于一个 Golden File END。
- 每请求 emitter隔离、连续恢复新 emitter、事件不串写。
- executor拒绝 END/release各一次。
- emitter 在 execution 绑定前/绑定后完成、超时或报错均会使 cancel 精确生效一次；RUNNING 场景不提前释放 lease。
- 正常 END 后 terminator 先置 TERMINATED 再 complete，completion callback 的 cancel 返回 false且不触发 token。
- Filter/TaskDecorator传播与请求/异步结束清理。
- core/runtime无 ThreadLocal依赖、ApexAgentContext无 SseEmitter。

### 架构符合性

HTTP与异步调度仅适配 runtime执行句柄；并发和END正确性不在platform重复维护。

## PLAT-03A 建立 PostgreSQL 配置与 Flyway schema

### 实现目标

让 platform 成为唯一 PostgreSQL 产品模块，建立三张核心表与 Flyway V1 migration；驱动、数据源、schema 同步切换，不兼容 MySQL历史数据。

### 涉及模块/类

- platform POM、`application.yml`/环境变量、Flyway migration。
- 表：`apex_agent_session`、`apex_agent_dialogue_message`、`apex_agent_dialogue_summary`。
- 当前 `db/memory-schema-postgresql.sql` 属于 memory能力，不作为核心表 migration直接复用。

### 核心流程

1. platform POM加入 PostgreSQL driver、Flyway PostgreSQL支持、Spring JDBC/MyBatis Plus中选定实现依赖；移除 MySQL。
2. 创建 `V1__create_core_agent_tables.sql`。
3. application配置只通过环境变量给 URL/user/password，不在共享资源写真实凭据。
4. Testcontainers空库执行 migration并检查 schema。

### 接口和数据结构

SQL 采用跨模块契约第11节，补充：

```sql
CREATE TABLE apex_agent_dialogue_message (
  id VARCHAR(64) PRIMARY KEY,
  session_id VARCHAR(128) NOT NULL,
  turn_no BIGINT NOT NULL,
  sort_no BIGINT NOT NULL,
  role VARCHAR(32) NOT NULL,
  message_type VARCHAR(64) NOT NULL,
  content TEXT,
  payload TEXT,
  compacted BOOLEAN NOT NULL DEFAULT FALSE,
  created_time TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_dialogue_session_sort UNIQUE (session_id, sort_no)
);
```

summary 增加 `compaction_id VARCHAR(64)` 用于单 Repository幂等提交；session snapshot所有集合/对象列为 TEXT，不用 JSONB。

### 关键实现逻辑

- 不创建 Turn/Iteration表或 current_iteration_no列。
- session_id、user_id、agent_key等查询字段独立列并建必要索引；不查询TEXT内部JSON。
- 时间统一 `_time` + TIMESTAMPTZ；Java映射 Instant。
- 不硬编码 `spring.profiles.active: dev`；部署显式选择 profile。若保留 dev文件，也必须 PostgreSQL且不含真实密码。
- 当前事实是 POM MySQL、application PostgreSQL且无 application-dev；本任务直接建立一致目标，不做“保留未提交PG修改”假设。

### 异常处理

- Flyway checksum/schema冲突让应用启动失败，不运行自动破坏性修复。
- 旧 MySQL数据无迁移脚本；发布说明明确新库/新schema。

### 测试方案

- 空 PostgreSQL migration、重复启动幂等。
- information_schema断言无 JSONB、turn/iteration表、current_iteration_no、`*_at`列。
- platform dependency tree无 MySQL driver。
- TEXT插入超长样本不截断。

### 架构符合性

数据库技术完全位于platform，core只看到Repository端口；表结构保持Session快照与Conversation分层。

## PLAT-03B 实现 Session/Conversation Repository Adapter

### 实现目标

将 common 1.0.0快照、对话、摘要映射到三张表，保证明确类型、长TEXT、排序、幂等和单Repository事务。

### 涉及模块/类

目标：`PostgresSessionRepository`、`PostgresConversationRepository`、`SessionSnapshotTextAdapterV1`、`DialogueMessageEntity/Mapper`、`DialogueSummaryEntity/Mapper`、`AgentSessionEntity/Mapper`。

推荐继续使用项目已有 MyBatis Plus做标量映射，对 upsert/compact使用自定义Mapper SQL；entity只在platform persistence包。

### 核心流程

Session save：snapshot -> V1 adapter -> 多个TEXT JSON + 标量 -> INSERT ON CONFLICT UPDATE。load反向转换并验证版本/不变量。

Conversation append：单事务逐条插入；冲突时按 entryId和完整内容判定幂等或冲突。compact：单事务更新 compacted flags + upsert summary/compactionId。

### 接口和数据结构

`SessionSnapshotTextAdapterV1` 显式提供：

```java
AgentSessionEntity encode(SessionSnapshot snapshot);
SessionSnapshot decode(AgentSessionEntity entity);
```

enabled/activated序列化为 JSON string array；runtime snapshot、definition、suspended分别用明确目标record。数据库 null suspended映射 common null；空集合写 `[]`。

### 关键实现逻辑

- 读取时先从runtime/definition JSON提取 schemaVersion并要求1.0.0；不把Tree/Map直接交core。
- entity不能实现/继承 common snapshot，Mapper不能出现在core/runtime。
- append批次内部本地事务；不同Repository无共同事务。
- 相同 entryId重复：查询现有并比较 session/turn/sort/role/type/content/payload，一致视成功，否则 RepositoryConflictException。
- compact重复 compactionId +同payload视成功；不同payload冲突。
- 所有TEXT使用数据库原生TEXT，无ORM长度注解。

### 异常处理

- JSON decode/版本/聚合不变量异常包装 RepositoryCorruptionException，带session/列名不带正文。
- unique sort冲突、entryId内容不一致明确失败。
- 单Repository事务失败回滚本方法所有SQL。

### 测试方案

- Session完整/无挂起、工具/Skill集合、嵌套多ToolCall round-trip。
- 长消息/摘要/工具结果/快照。
- append/compact幂等与冲突、排序。
- entity/Mapper包不泄漏到core/runtime。
- 非1.0.0明确拒绝，不测试升级成功。

### 架构符合性

Adapter把数据库行转换为common快照，core不感知ORM；JSON统一走JsonUtils。

## PLAT-03C 实现单 Repository 状态提交与跨 Repository 顺序编排

### 实现目标

用platform集成测试证明core规定的四类调用顺序和失败停止在真实Adapter上成立，不向core引入Spring事务或组合Repository。

### 涉及模块/类

CORE-03/05C/06/07A的PersistenceCoordinator、两个Postgres Repository、故障注入Decorator、Testcontainers。

### 核心流程

分别驱动新Turn、挂起、压缩、ToolResult，用记录型Decorator捕获方法序列；在每个序号抛RepositoryException，验证后续Hook/模型/工具没有调用。

### 接口和数据结构

测试 `PersistenceCallLog(sequence, repository, operation, idempotencyKey)`。生产不新增UnitOfWork。Adapter单个方法可用 `@Transactional`，但注解只在platform实现。

### 关键实现逻辑

- 新Turn：conversation.append(user) -> session.save。
- 挂起：session.save单操作 -> interaction event。
- 压缩：conversation.compact -> session.save -> PRE_MODEL/model。
- ToolResult：conversation.append(result) -> session.save -> next ToolCall。
- 部分提交重试复用entryId/compactionId；测试不仅断言“失败停止”，还验证第一步已成功时再次调用不会重复数据。

### 异常处理

- 后一步失败不执行跨Repository补偿或回滚声明；记录部分提交ID供诊断。
- 故障注入只用于测试Decorator，不在生产增加策略开关。

### 测试方案

- 四类顺序和每步failure matrix。
- 单Repository事务一致性。
- 部分提交后的幂等重试与内容冲突。
- 明确断言不存在TransactionTemplate/UnitOfWork依赖于core。

### 架构符合性

核心控制顺序与基础设施单操作事务分工清晰，遵守已确认“不提供跨Repository事务端口”。

## PLAT-03D 完成进程重启恢复测试

### 实现目标

用两个先后启动、绝不重叠的platform/runtime实例证明已持久化挂起状态可在进程重建后恢复原Turn/Iteration/ToolCall。

### 涉及模块/类

Testcontainers PostgreSQL、Spring context/runtime fixture、Postgres Repositories、CORE-07、request Publishers。

### 核心流程

1. context A NEW执行到挂起，等待session/interaction/END持久化完成。
2. 关闭 A，确认lease释放和资源关闭。
3. 可修改当前Agent properties制造配置漂移对照。
4. context B连接同库，HUMAN_RESPONSE恢复。
5. 验证 snapshot定义而非新Provider、继续剩余Hook/ToolCall并最终完成或再次挂起。

### 接口和数据结构

测试场景表包含：QUESTION、确认批准、拒绝、再次介入、全部Continue；每个样本保存旧Hook stable name实现于B registry，定义内容可变化但实现仍可解析。

### 关键实现逻辑

- 两实例不能同时运行，符合单实例限制；这不是分布式lease测试。
- B每次恢复创建新 emitter/Publisher/Once状态。
- 验证 enabledTools/activatedSkills、pre-hook IDs、前序 ToolResult、sortNo和definition recovery snapshot。
- 如果旧Hook实现完全从registry删除，恢复应明确失败，不用新定义替换。

### 异常处理

- 只在A两个Repository成功边界后模拟重启；部分提交场景归PLAT-03C，不宣称可自动恢复。
- 非1.0.0不在成功验收，另测显式拒绝。

### 测试方案

- 五类恢复分支中至少覆盖批准/拒绝/再次挂起/Continue。
- Provider load/AGENT_BUILD/model前生命周期在恢复工具阶段0次。
- long TEXT与多ToolCall前序结果。
- context A/B Publisher实例不同，END各一次。

### 架构符合性

恢复依靠中立快照和runtime注册表重建，请求级与进程级对象均不持久化。

## PLAT-04 完成平台协议、并发与前端兼容验收

### 实现目标

证明新platform可以替换legacy入口，HTTP/SSE/人在回路对前端零修改，PostgreSQL恢复和单实例部署边界有可复现证据。

### 涉及模块/类

platform全部入口、runtime、PostgreSQL、PRO Golden Files、前端测试/build、部署文档和切换配置。

### 核心流程

1. 运行platform端到端场景矩阵。
2. 捕获实际SSE逐事件对比Golden File。
3. 并发、线程池拒绝、模型/Hook/工具异常、最大Iteration。
4. PostgreSQL重启恢复。
5. 在前端源码零diff前提运行三项验证。
6. 通过G5后切默认启动入口，legacy仅回归。

### 接口和数据结构

验收报告每项记录 command、commit、开始/结束时间、结果、日志/报告路径、未验证理由。部署声明：replicas=1；禁止滚动重叠实例，升级采用停旧启新或先实现分布式Coordinator。

### 关键实现逻辑

- 实际SSE不是仅测试AgentEventFactory，必须经过Controller、runtime Once和SseEmitter。
- END精确且一次；HUMAN_IN_THE_LOOP的END不代表Turn完成；core 同步准备失败的完整响应只能包含 END。
- 配置迁移是部署前置：虽然前端零改动，现有全局/workspace Agent配置不会被新Provider兼容，必须提供目标完整YAML。
- 默认platform不注册memory/session_search/learning Hook。

### 异常处理

- 任一协议、恢复、409、数据库或前端验证失败都阻塞platform接管。
- 外部MCP/Skill测试使用Fake/临时资源，不依赖application.yml机器路径。
- 未运行项必须写未验证，不能按通过统计。

### 测试方案

- `mvn -f apex-agent/pom.xml test`及platform Testcontainers suite。
- Controller路径/Header/字段、NEW/HUMAN_RESPONSE、400/409、core构造/恢复失败END-only、emitter隔离、task reject。
- 模型失败、Hook warn继续、工具异常ToolResult、最大Iteration。
- `npm --prefix apex-frontend run test:run`、`typecheck`、`build`。
- `git diff -- apex-frontend/src` 为空。

### 架构符合性

该门槛同时验证平台适配、协议兼容和部署约束，只有通过后才允许删除legacy，保证架构切换可控。
