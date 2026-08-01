package org.gemo.apex.common.snapshot;

import org.gemo.apex.common.execution.ToolExecutionStatus;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ToolExecutionSnapshot(ToolCall toolCall, ToolExecutionStatus status, ToolResult result) {
    public ToolExecutionSnapshot {
        toolCall = nonNull(toolCall, "toolCall");
        status = nonNull(status, "status");
        if (result != null && (!toolCall.toolCallId().equals(result.toolCallId())
                || !toolCall.name().equals(result.toolName()))) {
            throw new IllegalArgumentException("ToolResult 必须匹配 ToolCall ID 和名称");
        }
    }
}
