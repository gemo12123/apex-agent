package org.gemo.apex.memory.persistence.convert;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageEntityConverterTest {

    @Test
    void fromPayloadShouldPreserveToolCallsForAssistantMessageWithoutText() {
        AssistantMessage original = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "ask_human", "{}")))
                .build();

        String payload = MessageEntityConverter.toPayload(original);

        Message restored = MessageEntityConverter.fromPayload(payload, "assistant", "");

        AssistantMessage assistantMessage = assertInstanceOf(AssistantMessage.class, restored);
        assertTrue(assistantMessage.hasToolCalls());
        assertEquals(1, assistantMessage.getToolCalls().size());
        assertEquals("call-1", assistantMessage.getToolCalls().getFirst().id());
    }
}
