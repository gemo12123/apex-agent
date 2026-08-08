package org.gemo.apex.common.hook.context;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;

public record PostToolCallContext(
        String sessionId, HookBinding binding, ToolCall toolCall, ToolResult toolResult)
        implements HookContextView {
    public PostToolCallContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        toolCall = nonNull(toolCall, "toolCall");
        toolResult = nonNull(toolResult, "toolResult");
    }
}
