package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ConversationWindowRequest(ConversationQuery query) {
    public ConversationWindowRequest {
        query = nonNull(query, "query");
    }
}
