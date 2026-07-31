# memory 模块详细设计

## 模块设计定位

`apex-agent-memory` 封存长期记忆、会话搜索和 Skill Learning。它参与父构建，但不在 runtime/platform 默认依赖图中；普通 Session/Conversation连续性和普通 Skill 不属于本模块。

目标包可沿用 `org.gemo.apex.memory` 子域名，减少无关重命名，但所有对 core/runtime/platform 具体类型的引用必须改为 common/extension。

## MEM-01 分离并迁移长期 Memory 与会话搜索

### 实现目标

把用户画像、事实/历史/Agent经验、召回、抽取、写入、管理和 session_search迁入独立模块；把当前 memory包中的核心Session/Conversation存储、压缩和用户Web上下文移出。

### 涉及模块/类

迁入 memory：

- `memory/model` 中 MemoryItem/类型/状态/召回包等长期模型。
- `memory/recall`、`extract`、`write`、长期 persistence repository/entity/mapper。
- `memory/search`、`SessionSearchTool`、`memory/web`管理能力。

迁出：

- `memory/session/*` -> core-extension端口、runtime/platform实现。
- `memory/conversation/*` -> core压缩门 + runtime默认实现。
- `memory/context/UserContext*` -> platform。
- `SessionRuntimeSnapshot` -> common snapshot重新设计。

### 核心流程

1. 生成逐文件迁移矩阵，标记 move/rewrite/delete-after-move。
2. 长期服务方法改为显式接收 userId/sessionId/agentKey等中立参数，不读SuperAgentContext或platform ThreadLocal。
3. Memory Hook/Service若需接主循环，只实现 extension端口，不在本期装配。
4. session_search查询 memory自有搜索文档表，不直接查询platform核心conversation表。
5. platform默认工具清单移除session_search。

### 接口和数据结构

memory自有 `MemoryConversationDocument` 保存搜索所需副本：documentId、sourceSessionId、userId、agentKey、sourceTurnNo、content、metadata、searchVector/embedding、createdTime。该表不是核心对话真相来源；未来显式集成通过 ingestion端口写入。

管理Controller直接从 `X-User-Id` request header取得userId并传service，不依赖platform UserContextHolder。因platform不依赖memory，该Controller不会进入默认应用。

### 关键实现逻辑

- 当前 `memory-schema-postgresql.sql` 的 agent_turn、agent_iteration、agent_session、agent_session_dialogue_* 不迁入memory目标schema。
- session_search为保留可编译/可测试能力，测试直接准备memory自有document/summary数据；本期不承诺platform自动同步数据。
- MemoryRecallPackage若仅memory内部使用留本模块；若未来Hook接口需要，抽成common中立MemoryAugmentation，不让core依赖具体MemoryItem。
- 长期Memory repository可以依赖PostgreSQL/pgvector，依赖只在memory POM。
- 管理URL若保留，文档标注只有显式部署memory模块的应用才暴露。

### 异常处理

- 默认platform未装配memory不是异常，不尝试Class.forName或可选Bean自动发现。
- 搜索表未建立/pgvector不可用在memory独立应用启动或调用时明确失败，不影响platform。
- 抽取/写入错误不可能影响默认Agent执行，因为无装配边。

### 测试方案

- 现有 recall/search/extract/write/management测试迁移并改用中立fixture。
- 架构扫描memory无SuperAgent/ApexAgent core实现、platform/runtime import。
- platform/runtime dependency tree无memory，默认工具无session_search。
- memory session search使用自有表，schema/source搜索无核心会话表名。

### 架构符合性

长期可选能力不再提供核心会话连续性，也不被主链依赖，符合memory封存边界。

## MEM-02 分离并封存 Skill Learning

### 实现目标

把Skill使用记录、经验抽取、批处理、调度和增强Hook完整迁入memory；普通Skill定义/加载/activate/resource读取留runtime，默认platform不注册learning Hook。

### 涉及模块/类

迁入：`skills/learning/*` 全部模型、Repository、entity/mapper、scheduler、extractor、prompt service、usage recorder/augment Hook和配置。

依赖common SkillDefinition、conversation slice和extension LifecycleHook；不依赖runtime `Skills`实现。

### 核心流程

- 显式集成时 usage recorder Hook接收PostToolCallContext，产生memory内部usage record。
- Scheduler从memory自有usage/conversation document读取，调用经验提取并保存。
- augment Hook按agent/skill读取经验，返回PostToolCall结果Patch。
- 默认runtime/platform不注册这些Hook或配置Binding。

### 接口和数据结构

`SkillUsageRecord`、`SkillExperienceMemory` 保持memory域模型；`SkillConversationSlice` 的消息改为common AgentMessageEntry投影或memory自有不可变副本，不引用Spring AI Message。

Learning schema的source turn/message引用指向memory自有document/usage数据，不外键依赖platform核心表。

### 关键实现逻辑

- `activatedSkills` 是Session运行状态，绝不迁入memory。
- augment返回中立Hook result，不直接改Prompt/Session；未来装配仍由core原子应用。
- 调度器只有memory显式Spring配置启用时创建；模块被当库引用也不应自动调度。
- 删除platform application默认的 `skillExperienceAugmentHook`、`skillUsageRecorderHook` Binding和 `apex.skills.learning`默认启用配置。
- 经验instructions不进入runtime默认模型上下文。

### 异常处理

- learning抽取失败记录memory任务失败/重试，不影响默认runtime。
- Hook若未来显式装配，其普通异常仍按core warn跳过策略；memory实现不自定义fail-fast。
- 配置启用但repository/schema缺失时memory显式上下文启动失败。

### 测试方案

- 迁移现有10个learning测试，替换Spring AI/SuperAgent fixture。
- runtime artifact无learning package；platform默认context无Hook Bean/Binding/scheduler。
- RUN-05普通Skill全部回归继续通过。
- memory Hook descriptor与common结果族契约测试。

### 架构符合性

普通Skill与学习能力分开，memory可以未来通过extension显式接入，但本期不塑造runtime/core。

## MEM-03 完成 memory 独立 schema、构建与隔离验收

### 实现目标

建立memory自有POM、schema和构建入口，使其独立编译测试、可选部署，但不复用三张platform核心表表达长期语义，也不进入默认应用。

### 涉及模块/类

- memory POM、Flyway/SQL资源、全部Repository。
- 父POM聚合和FND架构规则。
- platform ApplicationContext隔离测试。

### 核心流程

1. 从旧SQL提取长期memory、search、learning表，删除agent session/turn/iteration/dialogue核心表。
2. 为搜索需要创建memory自有document/summary表和索引。
3. memory模块独立跑schema和repository测试。
4. 父reactor编译/测试memory，但platform dependency/context检查无memory。

### 接口和数据结构

建议表前缀保持明确：`memory_user_profile`、`memory_execution_history`、`memory_agent_experience`、`memory_conversation_document`、`memory_conversation_summary`、`memory_skill_usage_record`、`memory_skill_experience`。若为减少代码迁移保留旧长期表名，也必须删除/重命名所有核心agent_session/dialogue表依赖。

pgvector extension与HNSW/GIN索引只出现在memory migration；platform核心migration不创建vector扩展。

### 关键实现逻辑

- memory POM可依赖Spring/MyBatis/PostgreSQL/pgvector相关外部库，但项目模块依赖只到common+core-extension。
- 不使用platform配置类或data source bean；显式部署者提供memory自己的配置。
- platform的component scan根包若为 `org.gemo.apex`，虽然无dependency不会加载；测试仍需保证未来误加dependency时memory配置非默认自动生效，建议memory自动配置使用显式enable条件。
- 发布说明明确默认能力减少：无长期召回、session_search、Skill Learning。

### 异常处理

- memory migration失败只阻塞memory显式部署/测试，不影响platform构建测试以外的启动。
- 父构建中Testcontainers测试按项目约定分类，缺Docker时不得伪称通过；单元测试仍执行。

### 测试方案

- memory独立 `test`、schema migration、repository round-trip。
- 父POM包含memory测试。
- dependency tree：platform/runtime/core/kit均无memory。
- platform默认context无Memory Controller/Repository/Scheduler/Tool/Hook。
- schema搜索无 `apex_agent_session/dialogue_*` 或旧 agent_session表依赖。

### 架构符合性

memory作为叶子模块参与构建但没有入边，数据表与主会话真相源分离，满足封存目标。
