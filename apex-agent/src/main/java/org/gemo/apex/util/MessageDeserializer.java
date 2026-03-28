package org.gemo.apex.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gemo.apex.constant.MessageFields;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MessageDeserializer extends JsonDeserializer<Message> {

    @Override
    public Message deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);

        String messageTypeValue = node.has(MessageFields.MESSAGE_TYPE)
                ? node.get(MessageFields.MESSAGE_TYPE).asText()
                : "";
        String content = node.has("content") ? node.get("content").asText() : "";

        Map<String, Object> properties = Collections.emptyMap();
        if (node.has("properties")) {
            properties = mapper.convertValue(node.get("properties"), new TypeReference<Map<String, Object>>() {
            });
        }

        MessageType messageType;
        try {
            messageType = MessageType.valueOf(messageTypeValue);
        } catch (IllegalArgumentException e) {
            throw new IOException("Unknown message type: " + messageTypeValue, e);
        }

        switch (messageType) {
            case USER:
                return new UserMessage(content);
            case SYSTEM:
                return new SystemMessage(content);
            case ASSISTANT:
                List<AssistantMessage.ToolCall> toolCalls = Collections.emptyList();
                if (node.has("toolCalls")) {
                    toolCalls = mapper.convertValue(node.get("toolCalls"),
                            new TypeReference<List<AssistantMessage.ToolCall>>() {
                            });
                }
                if (toolCalls == null || toolCalls.isEmpty()) {
                    return new AssistantMessage(content);
                }
                try {
                    java.lang.reflect.Constructor<AssistantMessage> c = AssistantMessage.class.getDeclaredConstructor(
                            String.class, Map.class, List.class, List.class);
                    c.setAccessible(true);
                    return c.newInstance(content, properties, toolCalls, Collections.emptyList());
                } catch (Exception e) {
                    throw new IOException("Failed to deserialize AssistantMessage", e);
                }
            case TOOL:
                List<ToolResponseMessage.ToolResponse> responses = Collections.emptyList();
                if (node.has("responses")) {
                    responses = mapper.convertValue(node.get("responses"),
                            new TypeReference<List<ToolResponseMessage.ToolResponse>>() {
                            });
                }
                try {
                    java.lang.reflect.Constructor<ToolResponseMessage> c = ToolResponseMessage.class
                            .getDeclaredConstructor(
                                    List.class, Map.class);
                    c.setAccessible(true);
                    return c.newInstance(responses, properties);
                } catch (Exception e) {
                    throw new IOException("Failed to deserialize ToolResponseMessage", e);
                }
            default:
                throw new IOException("Unknown message type: " + messageTypeValue);
        }
    }
}
