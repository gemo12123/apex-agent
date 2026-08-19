package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.required;

public record EndTurnLoop(String reason) implements LoopHookResult {
    public EndTurnLoop {
        reason = required(reason, "reason");
    }
}
