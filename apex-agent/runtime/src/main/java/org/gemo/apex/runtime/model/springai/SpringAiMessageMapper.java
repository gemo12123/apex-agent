package org.gemo.apex.runtime.model.springai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gemo.apex.common.message.*;
import org.gemo.apex.common.tool.*;
import org.springframework.ai.chat.messages.*;

import java.util.*;

public final class SpringAiMessageMapper {
    private final ObjectMapper json = new ObjectMapper();

    public Message toSpring(AgentMessageEntry e) {
        return switch (e.role()) {
            case SYSTEM -> new SystemMessage(Objects.toString(e.content(), ""));
            case USER -> new UserMessage(Objects.toString(e.content(), ""));
            case ASSISTANT -> new AssistantMessage(Objects.toString(e.content(), ""));
            case TOOL ->
                    ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse(Objects.toString(e.payload().get("toolCallId"), e.entryId()), Objects.toString(e.payload().get("toolName"), "tool"), Objects.toString(e.content(), "")))).build();
        };
    }

    public AssistantMessage.ToolCall toSpring(ToolCall c) {
        try {
            return new AssistantMessage.ToolCall(c.toolCallId(), "function", c.name(), json.writeValueAsString(c.arguments()));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public ToolCall fromSpring(AssistantMessage.ToolCall c, int i) {
        try {
            return new ToolCall(c.id(), c.name(), i, json.readValue(c.arguments(), Map.class), Map.of("springAiType", c.type()));
        } catch (Exception e) {
            throw new IllegalArgumentException("工具参数不是 JSON 对象", e);
        }
    }
}
