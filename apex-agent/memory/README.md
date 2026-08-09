# apex-agent-memory

该模块只保存长期 Memory、`session_search`、Memory 管理和 Skill Learning 的历史源码与资源，不提供可运行能力。

## 构建边界

- POM 固定为 `packaging=pom`，没有依赖、编译/测试插件或额外 source/resource root。
- 历史文件仅位于 `archive/main`；模块不含 `src/main`、`src/test`，不产出 class 或 jar。
- 归档源码保留旧 package、import、注解和 SQL，未适配当前框架，也未编译、未测试。
- 其他模块不得依赖、扫描或打包该归档。普通 Skill、资源读取和 `activatedSkills` 继续由 common/runtime 提供；可选激活工具与状态 Hook 由 kit 提供。

## 默认产品能力

默认产品不包含长期召回、`session_search`、Memory 管理或 Skill Learning。保留历史文件不表示这些能力可用。

## 未来恢复条件

如需恢复其中能力，必须另立设计，把选定代码迁入标准源码目录，并重新完成框架兼容、schema/ingestion、platform 集成、依赖、安全和测试评审；不得直接把 `archive` 注册为 Maven source/resource。

逐文件来源、旧依赖和说明见 [archive/MANIFEST.md](archive/MANIFEST.md)。
