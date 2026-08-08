package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.required;

public record EndTurnPreModelCall(String reason) implements PreModelCallHookResult {
    public EndTurnPreModelCall {
        reason = required(reason, "reason");
    }
}
