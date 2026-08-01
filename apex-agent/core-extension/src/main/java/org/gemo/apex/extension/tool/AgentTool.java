package org.gemo.apex.extension.tool;

import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolDefinition;
import org.gemo.apex.common.tool.ToolExecutionContext;
import org.gemo.apex.common.tool.ToolResult;

public interface AgentTool {
    ToolDefinition definition();

    ToolResult execute(ToolCall call, ToolExecutionContext context, ToolExecutionObserver observer);
}
