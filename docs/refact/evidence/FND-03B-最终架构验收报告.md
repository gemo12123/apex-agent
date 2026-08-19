# FND-03B 最终架构验收报告

> 日期：2026-08-04
> 结论：FND-03B 已完成，迁移豁免清零，达到 G6。

## 1. 最终模块规则

| 模块 | 包根 | 精确项目依赖 | 自动禁止边界 |
| --- | --- | --- | --- |
| protocol | `org.gemo.apex.protocol` | 无 | common/extension/core/runtime/platform、Spring AI、Servlet、数据访问 |
| common | `org.gemo.apex.common` | protocol | extension/core/runtime/platform、Spring/Spring AI、Servlet、ORM、JDBC |
| core-extension | `org.gemo.apex.extension` | protocol、common | core/runtime/platform、Spring、Servlet；20 个生产类型必须全部为无状态、无实现、无注解的 interface |
| core | `org.gemo.apex.core` | protocol、common、core-extension | runtime/platform/memory、Spring、Servlet、JDBC/ORM、MCP 客户端 |
| kit | `org.gemo.apex.kit` | protocol、common、core-extension | core/runtime/platform/memory、Spring、计划工具和状态机固定结果工厂 |
| runtime | `org.gemo.apex.runtime` | protocol、common、core-extension、core、kit | platform/memory、Spring IoC、Servlet；不得分发 AGENT_BUILD |
| platform | `org.gemo.apex.platform` | protocol、common、core-extension、runtime | core/memory 直接依赖与 core 构造语义复制；唯一 Spring Boot 入口和唯一 repackage 所有者 |
| memory | 无标准 Java 包 | 无，`packaging=pom` | 无标准 source/test/resource root、build/plugins 或产物；archive 不进入其他模块 classpath/产物 |

`Fnd03bArchitectureTest` 汇总检查模块清单、精确项目依赖、包根、技术栈、唯一产品入口、空迁移豁免、已删除旧类型、AGENT_BUILD 唯一分发位置和 memory 隔离；Spring/Fastjson 典型非法源码 fixture 能触发失败。core-extension、common、core、kit 的已有专项架构测试继续由父 reactor 执行。

## 2. 清理与依赖结论

- 父 reactor 精确包含 protocol、common、core-extension、core、kit、runtime、platform、memory；`legacy` 和 `architecture-tests` 目录不存在。
- 新 platform 的 `ApexApplication` 是唯一 `@SpringBootApplication`，platform 是唯一执行 Spring Boot `repackage` 的模块。
- `migration-exemptions.yml` 精确为 `exemptions: []`，R-13 和 legacy 专用迁移豁免均已清零。
- 七个代码模块标准生产/测试源码不依赖 legacy；标准生产源码无 Fastjson/Fastjson2、旧 SuperAgent/Plan/Stage/重复 Hook Runtime 等过期类型。
- 项目依赖树符合固定表；Spring AI 运行依赖中的 `spring-ai-model`、`spring-ai-commons`、`spring-ai-template-st` 均解析为 1.1.2，父 Enforcer `dependencyConvergence` 无例外通过。
- memory POM 为无 dependencies/build 的 `packaging=pom`，不存在 `src/main`、`src/test` 或 `target`；runtime/platform JAR 对归档代表类型和 `memory/archive` 均为 0 命中。

## 3. 实际验证

| 命令 | 结果 |
| --- | --- |
| `mvn -q -f apex-agent/pom.xml -pl platform -am "-Dtest=Fnd03bArchitectureTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | 成功；FND-03B 统一架构测试 5 项通过 |
| `mvn -f apex-agent/pom.xml test` | 成功；父 reactor 和 Enforcer 通过 |
| `mvn -f apex-agent/pom.xml -pl protocol,common,core-extension,core,kit,runtime,platform -am dependency:analyze` | 成功；七个代码模块均为 `No dependency problems found` |
| `mvn -f apex-agent/pom.xml dependency:tree "-Dincludes=org.gemo.apex:*,org.springframework.ai:*,com.alibaba.cloud.ai:*" -Dverbose` | 成功；项目边符合固定图，Spring AI 为 1.1.2 单一解析版本线 |
| `mvn -f apex-agent/pom.xml dependency:tree "-Dincludes=com.alibaba:fastjson,com.alibaba.fastjson2:*"` | 成功；所有模块均无匹配条目 |
| `mvn -f apex-agent/pom.xml verify` | 成功；父 reactor、Enforcer、模块依赖分析和打包通过 |
| `mvn -f apex-agent/pom.xml clean verify` | 成功；清除旧测试字节码后重新编译验证，37 份报告共 176 项测试，0 失败、0 错误、1 跳过 |

依赖树命令尝试更新本机 Maven 仓库的 SNAPSHOT metadata 时出现只读权限警告，但 reactor 最终均为 `BUILD SUCCESS`，树输出完整，不影响上述结论。

## 4. 未实际验证与接受项

- `PostgresRepositoryIntegrationTest` 因本机无 Docker 被 Testcontainers 跳过，1 项未实际运行。
- 按本轮用户明确说明，PG 数据库相关测试默认接受，不作为 FND-03B/G6 阻塞；本报告不将其描述为实际通过。
- CLEAN-03 不属于本轮范围，尚未执行。
