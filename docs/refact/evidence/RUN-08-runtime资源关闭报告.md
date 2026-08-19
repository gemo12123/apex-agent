# RUN-08 runtime 资源关闭报告

runtime Builder 通过 `ownedResource` 与 `borrowedResource` 明确所有权。自有资源按注册逆序关闭且幂等；借用资源不关闭。关闭门先拒绝新 execution，再向活动 execution 发出非阻塞取消命令；lease 仍由 execution 的实际终止路径释放，最后一个 execution 注销后关闭共享自有资源。

默认配置不创建 MCP、SubAgent、外部 Skill、线程池、进程或网络连接。MCP/HTTP 调用句柄向同一请求级 `CancellationToken` 注册主动取消；SubAgent 遇到 ASK_HUMAN/TOOL_CONFIRMATION 立即取消子请求并作为普通工具失败，不实现嵌套恢复。

验证由 `RuntimeContractTest` 覆盖：无 IoC 执行、Once END、lease、取消注册、内存幂等、File Provider 缓存、普通 Skill、MCP 参数隔离、SSE 分帧及 owned/borrowed 关闭顺序。
