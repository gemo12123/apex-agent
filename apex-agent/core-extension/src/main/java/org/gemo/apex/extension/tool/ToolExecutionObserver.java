package org.gemo.apex.extension.tool;

import org.gemo.apex.common.tool.CancellationToken;
import org.gemo.apex.protocol.event.AgentMessage;

public interface ToolExecutionObserver {
    /**
     * 上报工具执行进度；本期只允许 INVOCATION_DECLARED 与 INVOCATION_CHANGE， allowlist 由 core 提供的 observer 实现校验。
     */
    void onEvent(AgentMessage event);

    /** 返回与 ToolExecutionContext、模型 observer 相同的请求级取消令牌。 */
    CancellationToken cancellationToken();
}
