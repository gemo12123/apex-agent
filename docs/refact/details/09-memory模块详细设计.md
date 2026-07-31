# memory 模块详细设计

## 模块设计定位

`apex-agent-memory` 仅用于保存长期 Memory、`session_search`、管理能力和 Skill Learning 的历史源码与资源，方便后续整理历史思路。它不是本期可运行能力、可复用依赖或兼容当前框架的代码模块。

memory 保留八模块中的目录和 Maven 坐标，但 POM 固定为 `packaging=pom`。归档文件放在非标准 Maven source root，默认 compile/test/package 都不处理；memory 不声明 protocol、common、core-extension 或任何基础设施依赖，也不产出 class/jar。其他七个模块不得依赖、component-scan、Flyway-scan 或资源扫描归档目录。

目标目录：

```text
memory/
├── pom.xml                         # packaging=pom，无 dependencies/plugins
├── README.md                       # 归档边界、不可运行声明、未来启用条件
└── archive/
    ├── MANIFEST.md                 # 原路径、类别、归档原因、目标路径
    ├── main/java/                  # 原生产源码，保留旧 package/import
    └── main/resources/             # 旧 SQL、Prompt、配置等，仅作资料
```

不创建 `src/main`、`src/test` 或额外 source root；不迁移测试。

## MEM-01 归档长期 Memory、会话搜索与管理代码

### 实现目标

把用户画像、事实/执行历史/Agent 经验、召回、抽取、写入、管理、pgvector 和 `session_search` 的相关旧生产源码与资源完整移入 memory 归档区，同时把产品主链需要的 Session/Conversation 能力迁往其正式目标模块。

### 涉及模块/类

归档候选：

- `org.gemo.apex.memory` 下长期记忆 model、recall、extract、write、manage、web、长期 persistence 代码。
- `SessionSearchTool` 及其只服务于旧搜索实现的配置、Prompt、SQL。
- 旧 `memory-schema-postgresql.sql` 中与长期记忆、搜索相关的内容；整个文件可原样归档，不作为 migration 执行。

不归档为可用 memory 能力：

- 当前 Agent 必需的 Session/Conversation 端口和目标实现，分别迁入 core-extension、runtime/platform。
- 用户 Web 上下文，迁入 platform。
- 摘要压缩主链，迁入 core/runtime。

### 核心流程

1. 生成逐文件清单，标记 `archive`、`move-to-production-module` 或 `delete-after-replacement`，禁止凭包名前缀整包搬迁。
2. 先完成主链所需 Session/Conversation 替代实现，再从 legacy 移除对应旧实现，避免归档代码成为生产依赖。
3. 对长期 Memory/search/manage 文件使用保留历史的移动操作，放入 `memory/archive/main/...`；除解决归档路径重名外，不改 API、import、注解或业务逻辑。
4. 将旧 SQL/Prompt/配置放入 `archive/main/resources`，确保没有任何 Maven/Spring/Flyway 默认扫描路径指向它们。
5. 从 runtime/platform 默认工具、Bean、MapperScan、配置和依赖中删除 `session_search` 与 memory 引用。
6. 在 `archive/MANIFEST.md` 记录原路径、目标路径、功能类别、已知旧框架耦合和归档日期。

### 接口和数据结构

本任务不新增运行接口、领域模型、Repository、数据库表或 HTTP API。`MANIFEST.md` 每行至少记录：

```text
original_path | archived_path | category | known_dependencies | note
```

归档内保留的 Java/SQL 仅是历史材料；其中类型、表名、配置键和 URL 均不构成当前契约。

### 关键实现逻辑

- 归档文件不做 common/core-extension 适配；允许继续引用 `SuperAgentContext`、Spring、MyBatis、旧实体和旧表。
- 不建立 memory 自有 conversation document/summary，不设计 platform ingestion，不承诺 `session_search` 有实时数据。
- 不把 archive 配置为 `build-helper-maven-plugin` source，不复制到 resources，不发布 classifier jar。
- 主链清理必须以七个代码模块的 import、依赖树和 Spring/Flyway 扫描结果为准，不能以“文件已移动”替代隔离验证。

### 异常处理

- 某文件同时含核心会话与长期记忆职责时，先把生产所需逻辑重新实现到目标模块，再把原文件整体归档；不得让生产代码 import archive。
- 归档文件存在编译错误、旧依赖或不可运行配置不视为本期缺陷，但必须在 manifest 的 `known_dependencies`/`note` 标明。
- 若移动导致七个生产模块缺类或默认启动仍扫描 archive，MEM-01 不得签收。

### 测试方案

按已确认决策，不迁移、不新增、不运行 memory 单元或集成测试。只执行非行为验证：归档清单路径存在且无重复遗漏；七个代码模块源码、POM、component scan、MapperScan、Flyway location 和默认 Agent 配置均不引用 memory/archive；父构建不会编译归档 Java 或执行归档 SQL。

### 架构符合性

历史思路保留在依赖图之外，核心 Session/Conversation 能力进入正式端口和适配器，避免可选旧代码反向塑造 core/runtime/platform。

## MEM-02 归档 Skill Learning

### 实现目标

把 Skill 使用记录、经验抽取、批处理、调度、增强 Hook、Repository 和配置作为历史资料归档；普通 Skill 定义、加载、资源读取与 `activate_skill` 继续由 runtime 生产实现承担。

### 涉及模块/类

- 归档：`org.gemo.apex.skills.learning` 下的旧生产源码、相关 Mapper/entity、Prompt、SQL 和配置。
- 生产保留：common SkillDefinition/session activatedSkills，runtime SkillProvider/SkillActivator/文件资源读取。
- 生产删除：platform 默认 `skillExperienceAugmentHook`、`skillUsageRecorderHook` Binding，learning scheduler/Bean/Mapper 扫描与配置。

### 核心流程

1. 按 production Skill 与 learning Skill 划分文件；共享文件先提取生产最小逻辑到 common/runtime。
2. 将 learning 旧源码/资源原样移动到 archive 并登记 manifest，不为适配 Hook 结果族而改写。
3. 删除七个代码模块对 learning package、Bean、scheduler、Repository、表和配置键的引用。
4. 验证默认 Agent 定义只包含普通 Skill，不包含 learning Hook Binding。

### 接口和数据结构

本任务不定义可运行 `SkillUsageRecord`、`SkillExperienceMemory`、Hook descriptor 或 schema 契约。archive 中同名类型仅表示旧设计，不能被标准源码 import。

### 关键实现逻辑

- `activatedSkills` 是当前 session 运行态，必须由 common/runtime 正式实现，不能随 learning 归档。
- 不建立调度器启用条件、经验 ingestion、Hook Adapter 或 memory 数据源。
- 不为了让归档代码编译而把 Spring/MyBatis/模型依赖加回父 POM 或 memory POM。

### 异常处理

- learning 与普通 Skill 共享实现时，只迁走 learning 专属部分；生产 Skill 回归失败说明职责拆分错误，不能通过恢复 archive 依赖解决。
- archive 中过期 Prompt、SQL 或绝对路径只作历史材料，不进入构建资源；manifest 标注其风险。

### 测试方案

不迁移或运行 Skill Learning 测试。只运行 RUN-05 的普通 Skill 生产测试，并静态验证 runtime/platform artifact、默认 Spring context 和配置均无 learning 类、Hook、scheduler 或 Mapper；archive 不计入源码扫描基数。

### 架构符合性

普通 Skill 是 runtime 能力，历史学习方案是非编译资料，两者在构建和运行层彻底分离。

## MEM-03 收口归档边界与 reactor 占位模块

### 实现目标

建立可审计、不会进入生产构建的 memory 归档目录，并证明父 reactor、platform 启动和发布物均不包含归档 class、资源或依赖。

### 涉及模块/类

- memory `pom.xml`、`README.md`、`archive/MANIFEST.md`。
- 父 POM 模块列表和 FND/CLEAN 架构检查。
- 七个代码模块的 POM、源码扫描、资源扫描和最终发布物。

### 核心流程

1. 创建 `packaging=pom` 的 memory POM，只继承父工程基础属性，不声明 dependencies、build-helper、compiler/test 或 resource 插件配置。
2. 完成 manifest 完整性核对：每个被迁移旧生产文件恰有一条记录，目标文件存在，原标准源码路径不再存在。
3. 执行父 reactor；memory 只完成 POM 生命周期，七个代码模块正常编译测试。
4. 检查 platform/runtime 依赖树和 jar 内容，确认无 memory/archive。
5. 发布说明明确默认产品移除长期召回、`session_search`、Memory 管理和 Skill Learning；归档不表示能力可用。

### 接口和数据结构

memory 不发布 Java API 或 schema。`README.md` 必须明确：归档目的、不可运行/不可依赖、未做兼容与测试、未来恢复的前置设计项。

### 关键实现逻辑

- FND 架构扫描只扫描标准 `src/main`/`src/test`；另有归档边界检查专门禁止其他模块引用 archive 路径或把它注册为 source/resource。
- 父 reactor 中“模块成功”只表示 memory POM 生命周期成功，不能表述为 memory 源码编译或能力测试通过。
- 旧 SQL 不重命名为 Flyway 规范文件并放入生产 resources；平台 migration 只来自 platform。

### 异常处理

- memory POM 出现项目依赖、编译插件、测试插件或资源打包配置时直接失败。
- platform/runtime jar 或 classpath 出现 archive 文件、旧 memory 类或 SQL 时阻塞发布。
- manifest 缺项、重复项或目标不存在时阻塞 MEM-03；归档源码自身无法编译不阻塞。

### 测试方案

不执行 memory 行为测试。验收命令只包括父 reactor、七个代码模块既有测试、依赖树/jar 内容检查、标准 source root 检查和 manifest 完整性脚本；报告必须写“memory 未编译、未测试”，不得写“memory 测试通过”。

### 架构符合性

memory 作为无依赖、无字节码的叶子占位模块保留历史材料，生产架构仍是七个代码模块的单向无环依赖图。
