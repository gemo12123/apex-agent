package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.required;

public record EndTurnPostToolCall(String reason) implements PostToolCallHookResult {
    public EndTurnPostToolCall { reason = required(reason, "reason"); }
}
