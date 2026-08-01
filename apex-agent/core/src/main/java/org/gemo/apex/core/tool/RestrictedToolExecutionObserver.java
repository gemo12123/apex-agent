package org.gemo.apex.core.tool;

import org.gemo.apex.common.tool.CancellationToken;
import org.gemo.apex.core.event.AgentEventEmitter;
import org.gemo.apex.core.exception.IllegalToolEventException;
import org.gemo.apex.extension.tool.ToolExecutionObserver;
import org.gemo.apex.protocol.event.AgentMessage;
import org.gemo.apex.protocol.event.InvocationChangeMessage;
import org.gemo.apex.protocol.event.InvocationDeclaredMessage;

public final class RestrictedToolExecutionObserver implements ToolExecutionObserver {
    private final String invocationId;
    private final AgentEventEmitter emitter;
    private final CancellationToken token;

    public RestrictedToolExecutionObserver(String invocationId, AgentEventEmitter emitter,
                                           CancellationToken token) {
        this.invocationId = invocationId;
        this.emitter = emitter;
        this.token = token;
    }

    @Override
    public void onEvent(AgentMessage event) {
        if (!(event instanceof InvocationDeclaredMessage) && !(event instanceof InvocationChangeMessage)) {
            throw new IllegalToolEventException("工具只能发布 INVOCATION_DECLARED/INVOCATION_CHANGE");
        }
        Object actual = event.getContext() == null ? null : event.getContext().get("invocation_id");
        if (!invocationId.equals(actual)) {
            throw new IllegalToolEventException("工具事件 invocation_id 与当前调用不匹配");
        }
        emitter.publish(event);
    }

    @Override public CancellationToken cancellationToken() { return token; }
}
