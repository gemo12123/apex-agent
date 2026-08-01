package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.required;

public record ConversationCompactionTrigger(String sessionId, long turnNo, int iterationNo,
                                            String reason) {
    public ConversationCompactionTrigger {
        sessionId = required(sessionId, "sessionId");
        if (turnNo < 1) throw new IllegalArgumentException("turnNo 必须大于 0");
        if (iterationNo < 1) throw new IllegalArgumentException("iterationNo 必须大于 0");
        reason = required(reason, "reason");
    }
}
