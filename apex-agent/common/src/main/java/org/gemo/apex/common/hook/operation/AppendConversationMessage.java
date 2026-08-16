package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import java.util.Map;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.support.DomainValues;

/** POST_MESSAGE_COMPRESSION Hook 声明的持久化对话追加。 */
public record AppendConversationMessage(
        String operationId,
        MessageRole role,
        MessageType messageType,
        String content,
        Map<String, Object> payload) {
    public AppendConversationMessage {
        operationId = required(operationId, "operationId");
        role = nonNull(role, "role");
        messageType = nonNull(messageType, "messageType");
        if (messageType == MessageType.SUMMARY) {
            throw new IllegalArgumentException("压缩后追加不能使用 SUMMARY");
        }
        payload = DomainValues.jsonMap(payload, "payload");
    }
}
