# memory 历史归档清单

> 归档日期：2026-08-04
> 状态：迁移前清单已冻结；下表每个原路径只允许迁移一次。

## 所有权与前置证据

- 本工作集独占 `legacy` 中长期 Memory、`session_search`、管理和 Skill Learning 专属文件，以及 `memory/**`。
- 并行的 07-platform 工作集独占 `platform/**`、PostgreSQL migration 和共享构建文件；本清单不移动这些文件。
- Session/Conversation 替代证据：RUN-03 已完成并覆盖内存 Session/Conversation、窗口与压缩；PLAT-03/04 已落地 PostgreSQL Session/Conversation Repository 与恢复/查询测试。
- Skill 替代证据：RUN-05 已完成，普通 Skill、资源读取、`activate_skill` 与 `activatedSkills` 均由 common/runtime 持有。
- `memory/session`、`memory/conversation`、`memory/context` 是待 CLEAN 删除的 legacy 主链，不迁入本归档，也不构成当前 memory 能力。

## 迁移记录

字段：`original_path | archived_path | category | known_dependencies | note`

```text
apex-agent/legacy/src/main/java/org/gemo/apex/memory/config/MemoryConfigService.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/config/MemoryConfigService.java | archive | Spring、MemoryProperties | 长期 Memory 开关历史实现
apex-agent/legacy/src/main/java/org/gemo/apex/memory/extract/MemoryExtractionService.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/extract/MemoryExtractionService.java | archive | Spring AI、旧 Memory model/repository | 经验与长期记忆抽取
apex-agent/legacy/src/main/java/org/gemo/apex/memory/extract/PromptTemplateLoader.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/extract/PromptTemplateLoader.java | archive | Spring Resource | 旧 Prompt 加载
apex-agent/legacy/src/main/java/org/gemo/apex/memory/model/ExecutionTimeScope.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/model/ExecutionTimeScope.java | archive | 无 | 长期 Memory model
apex-agent/legacy/src/main/java/org/gemo/apex/memory/model/MemoryCategory.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/model/MemoryCategory.java | archive | 无 | 长期 Memory model
apex-agent/legacy/src/main/java/org/gemo/apex/memory/model/MemoryItem.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/model/MemoryItem.java | archive | Lombok、旧 Memory model | 长期 Memory model
apex-agent/legacy/src/main/java/org/gemo/apex/memory/model/MemoryQueryType.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/model/MemoryQueryType.java | archive | 无 | 长期 Memory model
apex-agent/legacy/src/main/java/org/gemo/apex/memory/model/MemoryRecallPackage.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/model/MemoryRecallPackage.java | archive | 旧 Memory model | 长期 Memory 召回载荷
apex-agent/legacy/src/main/java/org/gemo/apex/memory/model/MemoryStatus.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/model/MemoryStatus.java | archive | 无 | 长期 Memory model
apex-agent/legacy/src/main/java/org/gemo/apex/memory/model/MemoryType.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/model/MemoryType.java | archive | 无 | 长期 Memory model
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/convert/MemoryItemEntityConverter.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/convert/MemoryItemEntityConverter.java | archive | 旧 Memory entity/model | 长期 Memory 转换器
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/entity/AgentExperienceMemoryEntity.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/entity/AgentExperienceMemoryEntity.java | archive | MyBatis Plus、Lombok | 长期 Memory entity
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/entity/UserExecutionHistoryMemoryEntity.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/entity/UserExecutionHistoryMemoryEntity.java | archive | MyBatis Plus、Lombok | 长期 Memory entity
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/entity/UserProfileMemoryEntity.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/entity/UserProfileMemoryEntity.java | archive | MyBatis Plus、Lombok | 长期 Memory entity
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/mapper/AgentExperienceMemoryMapper.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/mapper/AgentExperienceMemoryMapper.java | archive | MyBatis Plus、旧 entity | 长期 Memory mapper
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/mapper/UserExecutionHistoryMemoryMapper.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/mapper/UserExecutionHistoryMemoryMapper.java | archive | MyBatis Plus、旧 entity | 长期 Memory mapper
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/mapper/UserProfileMemoryMapper.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/mapper/UserProfileMemoryMapper.java | archive | MyBatis Plus、旧 entity | 长期 Memory mapper
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/repository/InMemoryMemoryManageRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/repository/InMemoryMemoryManageRepository.java | archive | 旧 Memory repository/model | 管理实现
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/repository/InMemoryMemoryReadRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/repository/InMemoryMemoryReadRepository.java | archive | 旧 Memory repository/model | 召回实现
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/repository/InMemoryMemoryStore.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/repository/InMemoryMemoryStore.java | archive | 旧 Memory model | 内存长期 Memory store
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/repository/InMemoryMemoryWriteRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/repository/InMemoryMemoryWriteRepository.java | archive | 旧 Memory repository/model | 写入实现
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/repository/JdbcMemoryManageRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/repository/JdbcMemoryManageRepository.java | archive | MyBatis、旧 mapper/model | JDBC 管理实现
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/repository/JdbcMemoryReadRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/repository/JdbcMemoryReadRepository.java | archive | MyBatis、旧 mapper/model | JDBC 召回实现
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/repository/JdbcMemoryWriteRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/repository/JdbcMemoryWriteRepository.java | archive | MyBatis、旧 mapper/model | JDBC 写入实现
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/repository/MemoryManageRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/repository/MemoryManageRepository.java | archive | 旧 Memory model | 管理端口
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/repository/MemoryReadRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/repository/MemoryReadRepository.java | archive | 旧 Memory model | 召回端口
apex-agent/legacy/src/main/java/org/gemo/apex/memory/persistence/repository/MemoryWriteRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/persistence/repository/MemoryWriteRepository.java | archive | 旧 Memory model | 写入端口
apex-agent/legacy/src/main/java/org/gemo/apex/memory/recall/MemoryRecallService.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/recall/MemoryRecallService.java | archive | Spring、旧 context/repository | 长期 Memory 召回
apex-agent/legacy/src/main/java/org/gemo/apex/memory/search/MemoryEmbeddingService.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/search/MemoryEmbeddingService.java | archive | 无 | 搜索 embedding 端口
apex-agent/legacy/src/main/java/org/gemo/apex/memory/search/PgVectorLiteralFormatter.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/search/PgVectorLiteralFormatter.java | archive | PostgreSQL vector | 搜索辅助
apex-agent/legacy/src/main/java/org/gemo/apex/memory/search/PostgresSearchIndexUpdater.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/search/PostgresSearchIndexUpdater.java | archive | JDBC、旧 mapper/model | 搜索索引更新
apex-agent/legacy/src/main/java/org/gemo/apex/memory/search/PostgresSessionSearchRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/search/PostgresSessionSearchRepository.java | archive | JDBC、pgvector | 会话搜索实现
apex-agent/legacy/src/main/java/org/gemo/apex/memory/search/SearchIndexTextBuilder.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/search/SearchIndexTextBuilder.java | archive | 旧 session entity | 搜索文本构建
apex-agent/legacy/src/main/java/org/gemo/apex/memory/search/SessionSearchHit.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/search/SessionSearchHit.java | archive | Lombok | 会话搜索 DTO
apex-agent/legacy/src/main/java/org/gemo/apex/memory/search/SessionSearchQuery.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/search/SessionSearchQuery.java | archive | Lombok | 会话搜索 DTO
apex-agent/legacy/src/main/java/org/gemo/apex/memory/search/SessionSearchRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/search/SessionSearchRepository.java | archive | 旧搜索 DTO | 会话搜索端口
apex-agent/legacy/src/main/java/org/gemo/apex/memory/search/SessionSearchResult.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/search/SessionSearchResult.java | archive | Lombok | 会话搜索 DTO
apex-agent/legacy/src/main/java/org/gemo/apex/memory/search/SessionSearchScope.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/search/SessionSearchScope.java | archive | Lombok | 会话搜索 DTO
apex-agent/legacy/src/main/java/org/gemo/apex/memory/search/SessionSearchService.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/search/SessionSearchService.java | archive | 旧 MemoryProperties/search repository | 会话搜索服务
apex-agent/legacy/src/main/java/org/gemo/apex/memory/search/SpringAiMemoryEmbeddingService.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/search/SpringAiMemoryEmbeddingService.java | archive | Spring AI、旧 MemoryProperties | 搜索 embedding 实现
apex-agent/legacy/src/main/java/org/gemo/apex/memory/web/dto/MemoryUpdateRequest.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/web/dto/MemoryUpdateRequest.java | archive | Lombok | 管理 DTO
apex-agent/legacy/src/main/java/org/gemo/apex/memory/web/MemoryController.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/web/MemoryController.java | archive | Spring MVC、旧 UserContext | 管理接口
apex-agent/legacy/src/main/java/org/gemo/apex/memory/web/service/MemoryManageService.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/web/service/MemoryManageService.java | archive | 旧 Memory repository/model | 管理服务
apex-agent/legacy/src/main/java/org/gemo/apex/memory/write/MemoryLifecycleManager.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/write/MemoryLifecycleManager.java | archive | Spring、旧 context/extract/write | 长期 Memory 生命周期
apex-agent/legacy/src/main/java/org/gemo/apex/memory/write/MemoryWriteService.java | apex-agent/memory/archive/main/java/org/gemo/apex/memory/write/MemoryWriteService.java | archive | 旧 Memory repository/model | 长期 Memory 写入
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/InMemorySkillExperienceMemoryRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/InMemorySkillExperienceMemoryRepository.java | archive | learning model | Skill Learning repository
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/InMemorySkillUsageRecordRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/InMemorySkillUsageRecordRepository.java | archive | learning model | Skill Learning repository
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/JdbcSkillExperienceMemoryRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/JdbcSkillExperienceMemoryRepository.java | archive | MyBatis、learning mapper | Skill Learning repository
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/JdbcSkillUsageRecordRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/JdbcSkillUsageRecordRepository.java | archive | MyBatis、learning mapper | Skill Learning repository
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/model/SkillConversationSlice.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/model/SkillConversationSlice.java | archive | Spring AI Message | Skill Learning model
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/model/SkillExperienceMemory.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/model/SkillExperienceMemory.java | archive | Lombok | Skill Learning model
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/model/SkillSessionMessage.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/model/SkillSessionMessage.java | archive | Lombok | Skill Learning model
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/model/SkillUsageRecord.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/model/SkillUsageRecord.java | archive | Lombok | Skill Learning model
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/model/SkillUsageValidationResult.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/model/SkillUsageValidationResult.java | archive | Lombok | Skill Learning model
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/persistence/entity/SkillExperienceMemoryEntity.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/persistence/entity/SkillExperienceMemoryEntity.java | archive | MyBatis Plus、Lombok | Skill Learning entity
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/persistence/entity/SkillUsageRecordEntity.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/persistence/entity/SkillUsageRecordEntity.java | archive | MyBatis Plus、Lombok | Skill Learning entity
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/persistence/mapper/SkillExperienceMemoryMapper.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/persistence/mapper/SkillExperienceMemoryMapper.java | archive | MyBatis Plus、learning entity | Skill Learning mapper
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/persistence/mapper/SkillUsageRecordMapper.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/persistence/mapper/SkillUsageRecordMapper.java | archive | MyBatis Plus、learning entity | Skill Learning mapper
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/SkillExperienceAugmentHook.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/SkillExperienceAugmentHook.java | archive | 旧 Hook Runtime、learning repository | Skill Learning Hook
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/SkillExperienceExtractor.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/SkillExperienceExtractor.java | archive | Spring AI、learning model | Skill Learning 抽取
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/SkillExperienceLearningConfiguration.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/SkillExperienceLearningConfiguration.java | archive | Spring、MyBatis、scheduler | Skill Learning 配置
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/SkillExperienceLearningProperties.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/SkillExperienceLearningProperties.java | archive | Spring ConfigurationProperties | Skill Learning 配置
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/SkillExperienceMemoryRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/SkillExperienceMemoryRepository.java | archive | learning model | Skill Learning repository
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/SkillExperiencePromptService.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/SkillExperiencePromptService.java | archive | Spring Resource | Skill Learning Prompt
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/SkillExperienceScheduler.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/SkillExperienceScheduler.java | archive | Spring Scheduling、learning service | Skill Learning 调度
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/SkillUsageBatchService.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/SkillUsageBatchService.java | archive | learning repository/extractor | Skill Learning 批处理
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/SkillUsageMessageCollector.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/SkillUsageMessageCollector.java | archive | 旧 SessionContextStore | Skill Learning 消息收集
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/SkillUsageRecorderHook.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/SkillUsageRecorderHook.java | archive | 旧 Hook Runtime、learning repository | Skill Learning Hook
apex-agent/legacy/src/main/java/org/gemo/apex/skills/learning/SkillUsageRecordRepository.java | apex-agent/memory/archive/main/java/org/gemo/apex/skills/learning/SkillUsageRecordRepository.java | archive | learning model | Skill Learning repository
apex-agent/legacy/src/main/java/org/gemo/apex/tool/SessionSearchTool.java | apex-agent/memory/archive/main/java/org/gemo/apex/tool/SessionSearchTool.java | archive | Spring AI Tool、旧搜索服务 | session_search 工具
apex-agent/legacy/src/main/resources/db/memory-schema-postgresql.sql | apex-agent/memory/archive/main/resources/db/memory-schema-postgresql.sql | archive | PostgreSQL、pgvector、旧会话表 | 历史 SQL，不作为 Flyway migration
apex-agent/legacy/src/main/resources/prompts/memory/agent-experience-memory.st | apex-agent/memory/archive/main/resources/prompts/memory/agent-experience-memory.st | archive | 旧模板变量 | 长期 Memory Prompt
apex-agent/legacy/src/main/resources/prompts/memory/dialogue-summary.st | apex-agent/memory/archive/main/resources/prompts/memory/dialogue-summary.st | archive | 旧模板变量 | 已由 runtime 压缩替代
apex-agent/legacy/src/main/resources/prompts/memory/execution-history-memory.st | apex-agent/memory/archive/main/resources/prompts/memory/execution-history-memory.st | archive | 旧模板变量 | 长期 Memory Prompt
apex-agent/legacy/src/main/resources/prompts/memory/long-term-memory.st | apex-agent/memory/archive/main/resources/prompts/memory/long-term-memory.st | archive | 旧模板变量 | 长期 Memory Prompt
apex-agent/legacy/src/main/resources/prompts/skills/skill-experience-learning.st | apex-agent/memory/archive/main/resources/prompts/skills/skill-experience-learning.st | archive | 旧模板变量 | Skill Learning Prompt
```

归档文件保留旧 package、import、注解和 SQL；它们未适配当前框架、不会编译或测试，也不构成产品能力。
