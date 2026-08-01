package org.gemo.apex.common.hook.result;

import org.gemo.apex.common.tool.ToolResult;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ReturnToolResult(ToolResult result) implements PreToolCallHookResult {
    public ReturnToolResult { result = nonNull(result, "result"); }
}
