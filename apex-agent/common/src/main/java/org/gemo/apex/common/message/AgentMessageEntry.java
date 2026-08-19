package org.gemo.apex.common.message;

import static org.gemo.apex.common.support.DomainValues.*;

import java.time.Instant;
import java.util.Map;
import org.gemo.apex.common.support.DomainValues;

public record AgentMessageEntry(
        String entryId,
        String sessionId,
        long turnNo,
        long sortNo,
        MessageRole role,
        MessageType messageType,
        String content,
        Map<String, Object> payload,
        Instant createdTime) {
    public AgentMessageEntry {
        entryId = required(entryId, "entryId");
        sessionId = required(sessionId, "sessionId");
        nonNegative(turnNo, "turnNo");
        nonNegative(sortNo, "sortNo");
        role = nonNull(role, "role");
        messageType = nonNull(messageType, "messageType");
        payload = DomainValues.jsonMap(payload, "payload");
        createdTime = nonNull(createdTime, "createdTime");
    }
}
