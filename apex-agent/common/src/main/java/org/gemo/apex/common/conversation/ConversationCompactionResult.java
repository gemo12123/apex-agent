package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.required;

public record ConversationCompactionResult(String compactionId, String summary) {
    public ConversationCompactionResult {
        compactionId = required(compactionId, "compactionId");
        summary = required(summary, "summary");
    }
}
