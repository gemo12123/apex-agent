package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.required;

public record EndTurnPostModelCall(String reason) implements PostModelCallHookResult {
    public EndTurnPostModelCall {
        reason = required(reason, "reason");
    }
}
