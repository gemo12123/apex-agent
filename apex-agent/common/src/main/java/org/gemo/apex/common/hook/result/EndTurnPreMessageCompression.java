package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.required;

public record EndTurnPreMessageCompression(String reason)
        implements PreMessageCompressionHookResult {
    public EndTurnPreMessageCompression {
        reason = required(reason, "reason");
    }
}
