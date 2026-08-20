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
        Integer iterationNo,
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
        if (iterationNo != null && iterationNo < 1) {
            throw new IllegalArgumentException("iterationNo 必须大于 0");
        }
        role = nonNull(role, "role");
        messageType = nonNull(messageType, "messageType");
        payload = DomainValues.jsonMap(payload, "payload");
        createdTime = nonNull(createdTime, "createdTime");
    }

    public AgentMessageEntry(
            String entryId,
            String sessionId,
            long turnNo,
            long sortNo,
            MessageRole role,
            MessageType messageType,
            String content,
            Map<String, Object> payload,
            Instant createdTime) {
        this(entryId, sessionId, turnNo, sortNo, null, role, messageType, content, payload, createdTime);
    }
}
