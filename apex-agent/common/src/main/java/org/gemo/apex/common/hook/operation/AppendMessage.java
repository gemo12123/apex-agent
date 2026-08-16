package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.required;

import java.util.Map;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.support.DomainValues;

public record AppendMessage(
        String operationId,
        MessageRole role,
        MessageType messageType,
        String content,
        Map<String, Object> payload)
        implements MessageOperation {
    public AppendMessage {
        operationId = required(operationId, "operationId");
        role = DomainValues.nonNull(role, "role");
        messageType = DomainValues.nonNull(messageType, "messageType");
        if (messageType == MessageType.SUMMARY) {
            throw new IllegalArgumentException("不能通过 AppendMessage 追加 SUMMARY");
        }
        payload = DomainValues.jsonMap(payload, "payload");
    }
}
