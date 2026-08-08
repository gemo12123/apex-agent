package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.required;

public record EndTurnPostMessageCompression(String reason)
        implements PostMessageCompressionHookResult {
    public EndTurnPostMessageCompression {
        reason = required(reason, "reason");
    }
}
