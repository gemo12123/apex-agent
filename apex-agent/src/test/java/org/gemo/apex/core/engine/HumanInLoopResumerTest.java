package org.gemo.apex.core.engine;

import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class HumanInLoopResumerTest {

    @Mock
    private ConversationMemoryManager conversationMemoryManager;

    private HumanInLoopResumer humanInLoopResumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        humanInLoopResumer = new HumanInLoopResumer(conversationMemoryManager);
    }

    @Test
    void resumeShouldAppendMissingAskHumanResponse() {
        SuperAgentContext context = new SuperAgentContext();
        context.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
        context.setPendingToolResult(Map.of("call-1", "approved"));
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "ask_human", "{}")))
                .build();
        context.setMessages(new java.util.ArrayList<>(List.of(assistantMessage)));

        humanInLoopResumer.resume(context);

        verify(conversationMemoryManager).appendDialogueMessage(any(), any(ToolResponseMessage.class));
        assertEquals(ExecutionStatus.IN_PROGRESS, context.getExecutionStatus());
        assertNull(context.getPendingToolResult());
    }

    @Test
    void resumeShouldNotAppendWhenResponseAlreadyExists() {
        SuperAgentContext context = new SuperAgentContext();
        context.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "ask_human", "{}")))
                .build();
        ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "ask_human", "approved")))
                .build();
        context.setMessages(new java.util.ArrayList<>(List.of(assistantMessage, toolResponseMessage)));

        humanInLoopResumer.resume(context);

        verify(conversationMemoryManager, never()).appendDialogueMessage(any(), any());
        assertEquals(ExecutionStatus.IN_PROGRESS, context.getExecutionStatus());
    }
}
