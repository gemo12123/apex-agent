package org.gemo.apex.common.hook.context;

import org.gemo.apex.common.tool.ToolCall;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record PreToolCallContext(String sessionId, ToolCall toolCall, String invocationId,
                                 String proposedInterventionId) implements HookContextView {
    public PreToolCallContext {
        sessionId = required(sessionId, "sessionId");
        toolCall = nonNull(toolCall, "toolCall");
        invocationId = required(invocationId, "invocationId");
        proposedInterventionId = required(proposedInterventionId, "proposedInterventionId");
    }
}
