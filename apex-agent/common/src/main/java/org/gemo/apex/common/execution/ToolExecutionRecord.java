package org.gemo.apex.common.execution;

import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ToolExecutionRecord(ToolCall toolCall, ToolExecutionStatus status, ToolResult result) {
    public ToolExecutionRecord {
        toolCall = nonNull(toolCall, "toolCall");
        status = nonNull(status, "status");
        if (result != null && (!toolCall.toolCallId().equals(result.toolCallId())
                || !toolCall.name().equals(result.toolName()))) {
            throw new IllegalArgumentException("ToolResult 必须匹配 ToolCall ID 和名称");
        }
    }
}
