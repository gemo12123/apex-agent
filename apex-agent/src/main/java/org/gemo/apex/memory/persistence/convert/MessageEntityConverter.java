package org.gemo.apex.memory.persistence.convert;

import org.gemo.apex.util.JacksonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 消息对象与持久化字段之间的转换器。
 */
public final class MessageEntityConverter {

    private MessageEntityConverter() {
    }

    public static String toPayload(Message message) {
        return JacksonUtils.toJson(message);
    }

    public static Message fromPayload(String payload, String role, String content) {
        if (StringUtils.isNotBlank(payload)) {
            Message message = JacksonUtils.fromJson(payload, Message.class);
            if (message != null && StringUtils.isNotBlank(message.getText())) {
                return message;
            }
        }
        if ("user".equalsIgnoreCase(role)) {
            return new UserMessage(content);
        }
        if ("assistant".equalsIgnoreCase(role)) {
            return new AssistantMessage(content);
        }
        if ("tool".equalsIgnoreCase(role)) {
            return ToolResponseMessage.builder().responses(java.util.List.of()).build();
        }
        return new SystemMessage(content);
    }

    public static String resolveRole(Message message) {
        return switch (message.getMessageType()) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
            case SYSTEM -> "system";
        };
    }

    public static String resolveMessageType(Message message) {
        return message.getMessageType().name();
    }

    public static String resolveToolName(Message message) {
        if (message instanceof AssistantMessage assistantMessage
                && assistantMessage.getToolCalls() != null
                && !assistantMessage.getToolCalls().isEmpty()) {
            return assistantMessage.getToolCalls().getFirst().name();
        }
        if (message instanceof ToolResponseMessage toolResponseMessage
                && toolResponseMessage.getResponses() != null
                && !toolResponseMessage.getResponses().isEmpty()) {
            return toolResponseMessage.getResponses().getFirst().name();
        }
        return null;
    }

    public static String resolveToolCallId(Message message) {
        if (message instanceof AssistantMessage assistantMessage
                && assistantMessage.getToolCalls() != null
                && !assistantMessage.getToolCalls().isEmpty()) {
            return assistantMessage.getToolCalls().getFirst().id();
        }
        if (message instanceof ToolResponseMessage toolResponseMessage
                && toolResponseMessage.getResponses() != null
                && !toolResponseMessage.getResponses().isEmpty()) {
            return toolResponseMessage.getResponses().getFirst().id();
        }
        return null;
    }
}
