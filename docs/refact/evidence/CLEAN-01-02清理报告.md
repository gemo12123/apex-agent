# CLEAN-01/02 清理报告

> 日期：2026-08-04
> 范围：仅 CLEAN-01、CLEAN-02；未执行 CLEAN-03 或 FND-03B

## 1. 删除前接管证据

- 产品入口 `POST /api/sse/chat` 位于 platform `ChatController`，其调用的 `ChatService` 只依赖 `ApexAgentRuntime` 与 `ApexAgentExecution`，不依赖 legacy。
- 七个目标代码模块的源码和 POM 均无 legacy 依赖；删除前只有父 reactor 仍把 legacy 作为迁移期回归模块。
- core 的架构测试证明业务迭代循环唯一，`AgentEventFactoryTest` 证明运行事件上下文固定为 `mode=react` 且不产生 `stage_id`。
- 删除前执行 `mvn -f apex-agent/pom.xml test` 成功，证明新 core/runtime/platform 产品链路和 protocol 兼容 DTO 均有可运行基线。

## 2. CLEAN-01 删除结果

- 删除 ModeEnum、Plan/Stage、StageToolResolver/StageToolPlan/StagePromptBuilder、计划工具、Plan Prompt、运行模式分支与 TASK_THINK 生产逻辑。
- 保留 protocol 的 PLAN/TASK/STREAM_THINK 兼容 DTO、事件常量和 Golden File。
- runtime 回归测试新增默认执行事件断言，明确不生产 PLAN_DECLARED、PLAN_CHANGE、TASK_THINK_DECLARED、TASK_THINK_CHANGE。

## 3. CLEAN-02 删除结果

- 删除 219 个 legacy 模块文件，包括 SuperAgent 族、旧 SessionService、重复 Hook Runtime、死 Adapter、旧单模块持久化/配置/入口及其测试资源。
- 删除迁移期 `architecture-tests` 模块，将 CLEAN-01/02 所需的八模块结构守卫放入 platform 测试；FND-03B 的最终架构规则仍未执行。
- 父 POM 只保留 protocol、common、core-extension、core、kit、runtime、platform、memory 八个模块；memory 仍为非编译 `packaging=pom` 占位模块。
- 删除 Fastjson、Guava、Apache HttpClient、JUnit4、Hutool、MinIO、旧 MySQL/MyBatis、旧 agent-utils/commonmark 等仅由 legacy 使用的父级依赖管理项。
- 清空已到期迁移豁免，并启用无 excludes 的 dependency convergence。

## 4. 验证结果

- `mvn -f apex-agent/pom.xml test`（删除前）：通过。
- `mvn -q -f apex-agent/pom.xml test`（删除后）：通过；PostgreSQL/Testcontainers 因无 Docker 跳过，符合本轮用户给定环境边界。
- `mvn -q -f apex-agent/pom.xml -pl protocol,common,core-extension,core,kit,runtime,platform dependency:analyze`：通过，未报告 used-but-undeclared。
- `mvn -q -f apex-agent/pom.xml dependency:tree "-Dincludes=com.alibaba:fastjson,com.alibaba.fastjson2:*"`：通过且无匹配依赖。
- `mvn -q -f apex-agent/pom.xml -pl protocol,core,runtime,platform -am test "-Dtest=ProtocolGoldenFileTest,CoreArchitectureTest,AgentEventFactoryTest,RuntimeContractTest,ChatControllerIntegrationTest,CleanupArchitectureTest" "-Dsurefire.failIfNoSpecifiedTests=false"`：通过。
- `mvn -q -f apex-agent/pom.xml package -DskipTests`：通过；仅 platform 绑定 Spring Boot `repackage` 并生成可执行 Boot jar。
- 目标模块生产源码/POM 搜索：无 SuperAgent、PlanExecutor、ModeEnum、StageToolResolver、计划工具、旧 Hook Runtime 或 Fastjson import；命中仅剩用于阻止回归的测试守卫和 protocol 兼容 DTO。
- 目录检查：父工程仅保留八个目标模块目录。

## 5. 未验证项与后续边界

- PostgreSQL/Flyway、持久化和重启恢复集成测试未运行，原因是当前环境无 Docker/PostgreSQL；本轮按用户说明不作为 CLEAN-01/02 阻塞，但不得描述为通过。
- CLEAN-03 未执行；当前态文档、最终覆盖矩阵、前端三项验证和发布/回滚说明留待 FND-03B 完成后处理。
- 下一步必须回到 `00-工程基线与模块骨架.md` 执行 FND-03B，本工作集在此停止。
