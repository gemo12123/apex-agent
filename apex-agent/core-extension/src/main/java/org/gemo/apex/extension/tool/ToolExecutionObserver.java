package org.gemo.apex.extension.tool;

import org.gemo.apex.common.tool.CancellationToken;
import org.gemo.apex.protocol.event.AgentMessage;

public interface ToolExecutionObserver {
    /**
     * 上报工具内部的中间进度或嵌套调用；外层调用的基础声明与终态由 core 自动发送。只允许 INVOCATION_DECLARED 与
     * INVOCATION_CHANGE，allowlist 由 core 提供的 observer 实现校验。
     */
    void onEvent(AgentMessage event);

    /** 返回与 ToolExecutionContext、模型 observer 相同的请求级取消令牌。 */
    CancellationToken cancellationToken();
}
