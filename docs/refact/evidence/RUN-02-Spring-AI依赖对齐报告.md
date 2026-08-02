# RUN-02 Spring AI 依赖对齐报告

> 日期：2026-08-02

## 触发证据

- 基线同时声明 Spring AI MCP 1.1.2、client-chat 2.0.0-M1 和 Alibaba RC2。
- 删除 R-13 的 Spring AI/Spring Framework convergence 豁免后，2.0.0-M1 引入 Spring Framework 7.0.1，与既有 6.2.6 冲突，Enforcer 失败。
- 切换到仓库已声明的 1.1.2 后，Alibaba RC2 传递 commons 1.1.0；由父 POM统一管理 commons/model/client-chat 为 1.1.2 后收敛通过。

## 变更

| 坐标 | before | after | 所有权与理由 |
| --- | --- | --- | --- |
| `spring-ai-client-chat` | 2.0.0-M1 | 1.1.2 | 父 POM；选择仓库已有 MCP 版本线 |
| `spring-ai-model` | 2.0.0-M1 | 1.1.2 | 父 POM；runtime 直接使用真实模型类型 |
| `spring-ai-commons` | 1.1.0/1.1.2 | 1.1.2 | 父 POM；解决 Alibaba 传递冲突 |
| Spring Framework | 显式 context 6.2.6 + 多传递版本 | Spring Boot 3.4.5 BOM 管理 | 删除叶子 pin，Boot 版本未变 |

Spring Boot 保持 3.4.5；Alibaba/模型供应商 SDK 均未调整。runtime 子模块不固定 Spring AI 版本。

## 验证

- `mvn -q -pl runtime -am verify`：通过，包含 convergence、真实 Spring AI 类型契约和依赖分析。
- `mvn -q -pl legacy -am test`：通过，156 项测试；基线加载来源更新为 `spring-ai-model-1.1.2.jar`，关键 API 签名保持。
