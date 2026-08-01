package org.gemo.apex.common.hook.context;

import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record PostToolCallContext(String sessionId, ToolCall toolCall, ToolResult toolResult)
        implements HookContextView {
    public PostToolCallContext {
        sessionId = required(sessionId, "sessionId");
        toolCall = nonNull(toolCall, "toolCall");
        toolResult = nonNull(toolResult, "toolResult");
    }
}
