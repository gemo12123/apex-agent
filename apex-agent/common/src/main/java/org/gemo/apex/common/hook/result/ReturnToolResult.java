package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.tool.ToolResult;

public record ReturnToolResult(ToolResult result) implements PreToolCallHookResult {
    public ReturnToolResult {
        result = nonNull(result, "result");
    }
}
