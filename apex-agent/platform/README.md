# Apex Agent Platform

platform 是唯一 Spring Boot 可执行模块，使用 PostgreSQL 与 Flyway，不兼容旧 MySQL 配置或历史数据。

当前 session execution lease 仅在单个进程内生效。生产部署必须保持 `replicas=1`，升级采用停止旧实例后再启动新实例；共享 PostgreSQL 不等于分布式 lease，禁止滚动阶段出现实例重叠。

数据库连接通过 `APEX_DB_URL`、`APEX_DB_USER`、`APEX_DB_PASSWORD` 提供。Agent 必须按 `apex.platform.agents` 新 schema 提供完整定义，不读取或转换旧 global/workspace 配置。
