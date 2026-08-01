package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.required;

public record ConversationQuery(String sessionId) {
    public ConversationQuery {
        sessionId = required(sessionId, "sessionId");
    }
}
