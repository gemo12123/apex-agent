package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.required;

public record EndTurnPreToolCall(String reason) implements PreToolCallHookResult {
    public EndTurnPreToolCall { reason = required(reason, "reason"); }
}
