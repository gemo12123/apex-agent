package org.gemo.apex.common.conversation;

import org.gemo.apex.common.message.AgentMessageEntry;

import java.util.List;

import static org.gemo.apex.common.support.DomainValues.*;

public record ConversationCompactionRequest(String sessionId, String compactionId,
                                            List<AgentMessageEntry> sourceMessages) {
    public ConversationCompactionRequest {
        sessionId = required(sessionId, "sessionId");
        compactionId = required(compactionId, "compactionId");
        sourceMessages = immutableList(sourceMessages, "sourceMessages");
    }
}
