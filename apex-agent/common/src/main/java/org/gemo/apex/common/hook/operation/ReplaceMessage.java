package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.required;

import java.util.Map;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.support.DomainValues;

public record ReplaceMessage(
        String operationId,
        String targetEntryId,
        MessageRole role,
        MessageType messageType,
        String content,
        Map<String, Object> payload)
        implements MessageOperation {
    public ReplaceMessage {
        operationId = required(operationId, "operationId");
        targetEntryId = required(targetEntryId, "targetEntryId");
        role = DomainValues.nonNull(role, "role");
        messageType = DomainValues.nonNull(messageType, "messageType");
        if (messageType == MessageType.SUMMARY) {
            throw new IllegalArgumentException("不能通过 ReplaceMessage 生成 SUMMARY");
        }
        payload = DomainValues.jsonMap(payload, "payload");
    }
}
