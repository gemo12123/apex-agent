package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.required;

public record BlockTool(String reason) implements PreToolCallHookResult {
    public BlockTool { reason = required(reason, "reason"); }
}
