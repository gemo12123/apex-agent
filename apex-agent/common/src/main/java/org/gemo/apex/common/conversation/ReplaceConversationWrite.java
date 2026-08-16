package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import java.util.Map;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.support.DomainValues;

public record ReplaceConversationWrite(
        String targetEntryId,
        MessageRole role,
        MessageType messageType,
        String content,
        Map<String, Object> payload)
        implements ConversationWrite {
    public ReplaceConversationWrite {
        targetEntryId = required(targetEntryId, "targetEntryId");
        role = nonNull(role, "role");
        messageType = nonNull(messageType, "messageType");
        if (messageType == MessageType.SUMMARY) {
            throw new IllegalArgumentException("不能把普通消息替换为 SUMMARY");
        }
        payload = DomainValues.jsonMap(payload, "payload");
    }
}
