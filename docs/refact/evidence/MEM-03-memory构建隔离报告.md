# MEM-03 memory 构建隔离报告

> 日期：2026-08-04
> 结论：MEM-01～MEM-03 已完成；memory 仅为非编译历史归档和 `packaging=pom` 占位模块。

## 归档范围

- 76 个长期 Memory、`session_search`、管理与 Skill Learning 旧生产源码/资源已从 `legacy/src/main` 移至 `memory/archive/main`。
- `archive/MANIFEST.md` 逐文件记录原路径、目标路径、类别、旧依赖和说明。
- 普通 Skill、资源读取、`activate_skill` 和 `activatedSkills` 保留在 common/runtime；归档未包含这些生产实现。
- legacy 中对长期召回、Turn 完成写入、搜索索引、`session_search` 工具、learning Hook/scheduler/Mapper 与配置的运行引用已移除。
- 旧 memory/learning 测试未迁移；memory 未编译、未测试。

## 自动边界验证

`MigrationArchitectureTest.memoryArchiveIsACompleteNonCompiledPomModule` 校验：

- memory POM 的 packaging 为 `pom`，且无 dependency、plugin、sourceDirectory 或 resource 声明；
- memory 不存在 `src/main`、`src/test`；
- MANIFEST 原路径唯一、目标路径唯一、原路径消失、目标文件存在；
- MANIFEST 记录数与 archive 实际文件数一致；
- 七个代码模块的标准源码/测试目录不引用 `memory/archive`。

## 实际验证

1. `mvn -f apex-agent/pom.xml -pl architecture-tests -am "-Dtest=MigrationArchitectureTest" test`
   - 成功；4 项测试通过，0 失败、0 错误、0 跳过。
2. `mvn -f apex-agent/pom.xml -pl runtime -am "-Dtest=RuntimeContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
   - 成功；11 项测试通过，覆盖 RUN-05 普通 Skill 激活、幂等与资源读取约束。
3. `mvn -f apex-agent/pom.xml clean verify`
   - 成功；父 reactor、七个代码模块、legacy 和 architecture-tests 均通过。
   - memory 在 reactor 中显示为 `[pom]`，没有 compile/test/package jar 行为。
   - clean 后 legacy surefire 报告仅包含 26 个非 memory/learning 测试类，证明归档测试未因旧 `target/test-classes` 缓存被执行。
   - platform 有 1 项 PostgreSQL/Testcontainers 集成测试因无 Docker 跳过，属于 07-platform 已记录阻塞。
4. `mvn -f apex-agent/pom.xml -pl runtime,platform -am dependency:tree "-Dincludes=org.gemo.apex:apex-agent-memory"`
   - 成功；runtime/platform 及其依赖链未输出 `apex-agent-memory`。
   - Maven 尝试更新本地 SNAPSHOT 元数据时因沙箱目录无写权限输出警告，不影响依赖树结果和最终成功状态。
5. runtime/platform jar 内容扫描
   - 对 `memory/archive`、旧 memory/learning 包、`SessionSearch`、旧 SQL/Prompt 的命中均为 0。
   - `memory/target` 中 jar 数为 0、class 数为 0。
6. 标准源码静态扫描
   - 七个代码模块中 `memory/archive`、`apex-agent-memory`、`skills.learning`、`session_search` 和 learning Hook 均为 0 命中。
   - legacy 标准生产源码中 `skills.learning`、`session_search`、`MemoryRecallService` 和 `MemoryLifecycleManager` 均为 0 命中。

## 产品边界与风险

- 默认产品没有长期召回、`session_search`、Memory 管理或 Skill Learning；归档不表示能力可用。
- 归档保留旧框架、Spring AI、MyBatis、pgvector、旧表和旧上下文引用，可能无法编译或运行，这是本工作集明确接受的边界。
- 本轮未设计 memory schema、ingestion 或 platform 集成，也未运行任何 memory 行为测试。
- 未来恢复必须另立设计并迁入标准源码目录，重新完成依赖、数据、安全、框架兼容和测试评审。
