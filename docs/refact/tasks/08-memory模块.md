# memory 模块任务

> 模块职责：把长期 Memory、会话搜索、管理和 Skill Learning 的旧源码/资源保存为非编译历史归档
> 当前总体进度：已完成（2026-08-04）；长期 Memory、搜索、管理和 Skill Learning 已退出标准源码，memory 为无依赖、无字节码的非编译归档占位模块

## MEM-01 归档长期 Memory、会话搜索与管理代码

- **任务名称**：分离主链职责并原样归档长期 Memory 相关旧代码。
- **任务目标**：保留历史实现供后续整理，同时确保当前 Session/Conversation 正式实现不依赖归档。
- **当前进度**：已完成（2026-08-04）。76 个归档文件中的长期 Memory/search/manage 部分已迁入非标准目录；legacy 默认工具、索引维护、生命周期写入、Mapper 扫描与配置引用已移除。
- **设计依据**：已确认 Q-16；设计文档第 5.8、阶段 7；架构文档第 5.8、15.3 节。
- **涉及范围**：长期 Memory model/recall/extract/write/manage/persistence、`session_search`、旧 SQL/Prompt/配置；排除主链 Session/Conversation 正式实现。
- **前置依赖**：FND-02、CORE/RUN/PLAT 对 Session/Conversation 的替代任务。
- **具体执行内容**：
  1. 建立逐文件 `archive/move-to-production-module/delete-after-replacement` 清单。
  2. 先完成主链 Session/Conversation 替代，再移除 legacy 对旧实现的运行依赖。
  3. 将长期 Memory/search/manage 生产源码与资源原样移动到 `memory/archive/main`，不改 API/import 以追求编译。
  4. 从 runtime/platform 默认工具、Bean、MapperScan、配置和依赖中移除 memory 与 `session_search`。
  5. 在 MANIFEST 记录原路径、目标路径、类别、旧依赖与说明。
- **预期产出**：非编译归档源码/资源、迁移清单、生产链路隔离证据。
- **验收标准**：
  - 清单中的归档文件均存在且原标准源码路径已清理。
  - 七个代码模块无 memory/archive import、依赖或扫描配置；默认工具无 `session_search`。
  - 父构建不编译归档 Java、不执行归档 SQL。
- **限制条件或注意事项**：不做 common/core-extension 适配，不设计 memory schema/ingestion，不迁移或新增 memory 测试；归档源码存在旧引用或不可编译是已接受边界。

## MEM-02 归档 Skill Learning

- **任务名称**：迁移 Skill Learning 历史源码并保留普通 Skill 生产能力。
- **任务目标**：让 learning 代码退出生产构建，同时保证普通 Skill、资源读取和 `activate_skill` 继续由 runtime 工作。
- **当前进度**：已完成（2026-08-04）。RUN-05 已确认完成，Skill Learning 源码、Prompt、Hook、scheduler、Repository 与配置已归档；普通 Skill 回归 11 项通过。
- **设计依据**：已确认 Q-16；设计文档第 5.8、阶段 7；架构文档第 5.8、6.4 节。
- **涉及范围**：`org.gemo.apex.skills.learning`、使用记录、经验抽取/调度/增强 Hook、Repository、SQL、Prompt 和配置。
- **前置依赖**：MEM-01、RUN-05。
- **具体执行内容**：
  1. 划分普通 Skill 与 learning 专属文件，共享生产逻辑先迁入 common/runtime。
  2. 将 learning 旧生产源码/资源原样移动到 archive 并登记 MANIFEST。
  3. 移除 platform/runtime 的 learning Bean、Hook Binding、scheduler、Mapper 和配置。
  4. 运行 RUN-05 普通 Skill 回归，确认没有通过 archive 补依赖。
- **预期产出**：Skill Learning 非编译归档和普通 Skill 隔离记录。
- **验收标准**：
  - runtime artifact 无 learning class，platform 默认 context 无 learning Hook/scheduler/Mapper。
  - 普通 Skill 生产测试通过。
  - archive 未注册为 Maven source/resource。
- **限制条件或注意事项**：不迁移或运行 Skill Learning 测试，不设计经验数据源或适配当前 Hook 框架；`activatedSkills` 仍是 common/runtime 的正式 session 状态。

## MEM-03 收口归档边界与 reactor 占位模块

- **任务名称**：建立无依赖、无字节码的 memory 占位模块和可审计归档。
- **任务目标**：保留八模块目录，同时证明 memory 不参与生产编译、测试、资源打包或运行。
- **当前进度**：已完成（2026-08-04）。memory POM/README/MANIFEST、自动归档边界守卫与构建隔离报告已完成；父 reactor verify 成功，runtime/platform jar 无归档内容，memory 产出 0 jar、0 class。
- **设计依据**：已确认 Q-16；设计文档第 4.1、5.8、22 节；架构文档第 4.2、15.3、18.1 节。
- **涉及范围**：memory POM/README/MANIFEST、父 POM、七个代码模块依赖树和发布物、FND/CLEAN 检查。
- **前置依赖**：MEM-01、MEM-02、FND-03A。
- **具体执行内容**：
  1. memory POM 使用 `packaging=pom`，不声明 dependencies、编译/测试插件或额外 source/resource root。
  2. 校验 MANIFEST 每个原路径恰好一条、目标存在且类别明确。
  3. 运行父 reactor 和七个代码模块测试；检查 platform/runtime dependency tree 与 jar 内容。
  4. 发布说明明确默认产品没有长期召回、`session_search`、Memory 管理或 Skill Learning，归档不表示能力可用。
- **预期产出**：memory 占位 POM、README、完整 MANIFEST 和构建隔离报告。
- **验收标准**：
  - memory 不含标准 `src/main`/`src/test`，不产出 class/jar，不运行测试。
  - 无模块依赖 memory，发布物和运行 classpath 无 archive 文件。
  - 报告明确写“memory 未编译、未测试”，父 reactor 成功不得误述为 memory 能力通过。
- **限制条件或注意事项**：未来启用必须另立设计，将选定源码迁入标准 source root，并重新完成框架兼容、schema/data、依赖、安全和测试评审。
