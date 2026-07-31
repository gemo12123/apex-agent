# memory 模块任务

> 模块职责：独立封存长期记忆、会话搜索和 Skill Learning；参与构建但不进入默认链路
> 当前总体进度：未开始；现有 memory 包同时包含核心会话存储和可选长期能力

## MEM-01 分离并迁移长期 Memory 与会话搜索

- **任务名称**：把用户画像、事实、历史、经验、召回和 session_search 迁入独立 memory。
- **任务目标**：保留现有可选能力的代码与测试，同时移除其对 core/runtime/platform 默认链路的塑形。
- **当前进度**：未开始。
- **设计依据**：设计文档第 5.8、12.4、20 节阶段 7；架构文档第 5.8、15.3 节。
- **涉及范围**：长期 Memory 实体、Repository、召回/抽取/写入/管理、pgvector、session_search；排除核心 Session/Conversation Repository。
- **前置依赖**：FND-02、COM-01/04、EXT-01。
- **具体执行内容**：
  1. 盘点 memory 包，区分核心会话连续性与长期记忆能力。
  2. 核心 Session/Conversation 端口/实现分别迁往 core-extension、runtime/platform。
  3. 将长期记忆、搜索和管理迁入 memory。
  4. 适配中立 common/扩展接口，不反向依赖 runtime/platform/core 具体实现。
  5. 从 platform 默认工具和配置中移除 session_search。
- **预期产出**：独立长期 Memory 代码、迁移映射和回归测试。
- **验收标准**：
  - memory 不包含 runtime 必需的 Session/Conversation 默认实现。
  - platform/runtime 默认启动时不注册 session_search。
  - memory 独立编译与既有长期能力单元测试通过。
  - 其他模块依赖树均无 memory。
- **限制条件或注意事项**：本期是封存而非重新接入；不得删减现有长期能力，也不得为了默认可用新增集成模块。

## MEM-02 分离并封存 Skill Learning

- **任务名称**：迁移 Skill 使用记录、经验抽取、调度和增强。
- **任务目标**：让普通 Skill 留在 runtime，Skill Learning 全部归入 memory 且默认不装配。
- **当前进度**：未开始。
- **设计依据**：设计文档第 5.8、12.3～12.4 节；架构文档第 5.8、6.4 节。
- **涉及范围**：`org.gemo.apex.skills.learning`、使用记录、经验抽取/调度/增强 Hook、相关 Repository 和配置。
- **前置依赖**：MEM-01、RUN-05。
- **具体执行内容**：
  1. 迁移全部 learning 子包与自有存储。
  2. 从 runtime/platform 注册表和默认 Agent 配置移除 `skillExperienceAugmentHook`、`skillUsageRecorderHook`。
  3. 保证普通 Skill 加载、activate_skill 和资源读取仍由 runtime 提供。
  4. 保留 memory 内部单元测试，但不建立默认装配。
- **预期产出**：独立 Skill Learning 子域和默认链路隔离测试。
- **验收标准**：
  - runtime artifact 无 learning 包、经验记录和调度代码。
  - platform 默认启动无 Skill Learning Hook Bean/Binding。
  - RUN-05 普通 Skill 回归测试保持通过。
  - memory 内 Skill Learning 测试独立通过。
- **限制条件或注意事项**：activatedSkills 属于普通 session 运行状态，不得错误迁入 memory；不恢复历史经验注入到默认模型上下文。

## MEM-03 完成 memory 独立 schema、构建与隔离验收

- **任务名称**：收口 memory 的独立构建和数据边界。
- **任务目标**：使 memory 参与父 POM 编译/测试但不进入 platform 默认启动依赖图，其 schema 不复用核心会话表表达长期语义。
- **当前进度**：未开始。
- **设计依据**：设计文档第 5.8、20 节阶段 7、22 节；架构文档第 11.1、15.3、18.1 节。
- **涉及范围**：memory POM、自有 schema/Repository、架构测试、默认应用依赖树。
- **前置依赖**：MEM-01、MEM-02、FND-03A；PLAT-03A 仅用于确认 schema 边界，不形成依赖。
- **具体执行内容**：
  1. 整理 memory 自有持久化 schema 和 Repository。
  2. 检查 memory 只依赖 common + core-extension 及其自身基础设施依赖。
  3. 增加“无模块依赖 memory”和“platform 默认上下文不装配 memory”的测试。
  4. 在发布说明记录默认能力变化。
- **预期产出**：可独立构建的 memory artifact、schema 和隔离报告。
- **验收标准**：
  - 父 POM 会编译/测试 memory，但 platform dependency tree 不含 memory。
  - memory schema 与 `apex_agent_session/dialogue_*` 核心表职责分离。
  - 默认 runtime/platform 运行不需要 memory 数据库或 pgvector。
- **限制条件或注意事项**：若产品仍要求默认暴露 Memory 管理接口，属于范围变更，必须重新确认；不得在本任务中隐式恢复装配。
