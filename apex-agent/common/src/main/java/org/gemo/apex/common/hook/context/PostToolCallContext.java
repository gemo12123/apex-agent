package org.gemo.apex.common.hook.context;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.shared.SharedDataStore;
import org.gemo.apex.common.shared.SharedDataStores;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;

public record PostToolCallContext(
        String sessionId,
        HookBinding binding,
        ToolCall toolCall,
        ToolResult toolResult,
        SharedDataStore sharedData)
        implements HookContextView {
    public PostToolCallContext(
            String sessionId, HookBinding binding, ToolCall toolCall, ToolResult toolResult) {
        this(sessionId, binding, toolCall, toolResult, SharedDataStores.create());
    }

    public PostToolCallContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        toolCall = nonNull(toolCall, "toolCall");
        toolResult = nonNull(toolResult, "toolResult");
        sharedData = nonNull(sharedData, "sharedData");
    }
}
