package org.gemo.apex.runtime.model.springai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.gemo.apex.common.agent.PrefixDeveloperMessage;
import org.gemo.apex.common.message.*;
import org.gemo.apex.common.tool.*;
import org.springframework.ai.chat.messages.*;

public final class SpringAiMessageMapper {
    private final ObjectMapper json = new ObjectMapper();

    public Message toSpring(PrefixDeveloperMessage message) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            default -> throw new IllegalArgumentException("PrefixDeveloperMessage 角色不受支持");
        };
    }

    public Message toSpring(AgentMessageEntry e) {
        return switch (e.role()) {
            case SYSTEM -> new SystemMessage(Objects.toString(e.content(), ""));
            case USER -> new UserMessage(Objects.toString(e.content(), ""));
            case ASSISTANT ->
                    AssistantMessage.builder()
                            .content(Objects.toString(e.content(), ""))
                            .toolCalls(toolCalls(e))
                            .build();
            case TOOL ->
                    ToolResponseMessage.builder()
                            .responses(
                                    List.of(
                                            new ToolResponseMessage.ToolResponse(
                                                    Objects.toString(
                                                            e.payload().get("toolCallId"),
                                                            e.entryId()),
                                                    Objects.toString(
                                                            e.payload().get("toolName"), "tool"),
                                                    Objects.toString(e.content(), ""))))
                            .build();
        };
    }

    public AssistantMessage.ToolCall toSpring(ToolCall c) {
        try {
            return new AssistantMessage.ToolCall(
                    c.toolCallId(), "function", c.name(), json.writeValueAsString(c.arguments()));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public ToolCall fromSpring(AssistantMessage.ToolCall c, int i) {
        try {
            return new ToolCall(
                    c.id(),
                    c.name(),
                    i,
                    json.readValue(c.arguments(), Map.class),
                    Map.of("springAiType", c.type()));
        } catch (Exception e) {
            throw new IllegalArgumentException("工具参数不是 JSON 对象", e);
        }
    }

    private List<AssistantMessage.ToolCall> toolCalls(AgentMessageEntry entry) {
        Object value = entry.payload().get("toolCalls");
        if (!(value instanceof List<?> calls)) {
            return List.of();
        }
        return calls.stream().map(this::toSpringToolCall).toList();
    }

    private AssistantMessage.ToolCall toSpringToolCall(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("ASSISTANT 工具调用必须是对象");
        }
        try {
            Object arguments = raw.containsKey("arguments") ? raw.get("arguments") : Map.of();
            return new AssistantMessage.ToolCall(
                    Objects.toString(raw.get("toolCallId"), ""),
                    "function",
                    Objects.toString(raw.get("name"), ""),
                    json.writeValueAsString(arguments));
        } catch (Exception e) {
            throw new IllegalArgumentException("ASSISTANT 工具调用参数无法序列化", e);
        }
    }
}
