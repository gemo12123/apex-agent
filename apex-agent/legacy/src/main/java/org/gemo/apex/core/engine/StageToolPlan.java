package org.gemo.apex.core.engine;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public record StageToolPlan(List<ToolCallback> callableTools, List<ToolCallback> promptDescribedTools) {
}
