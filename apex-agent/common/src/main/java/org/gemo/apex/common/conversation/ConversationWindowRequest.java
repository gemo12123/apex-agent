package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ConversationWindowRequest(
        ConversationQuery query, int maxMessages, int retainRecentMessages) {
    public ConversationWindowRequest {
        query = nonNull(query, "query");
        if (maxMessages < 1) {
            throw new IllegalArgumentException("maxMessages 必须大于 0");
        }
        if (retainRecentMessages < 0 || retainRecentMessages > maxMessages) {
            throw new IllegalArgumentException("retainRecentMessages 必须位于 0..maxMessages");
        }
    }
}
