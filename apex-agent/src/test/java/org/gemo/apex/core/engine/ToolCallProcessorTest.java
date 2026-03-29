package org.gemo.apex.core.engine;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.exception.HumanInTheLoopException;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolCallProcessorTest {

    @Mock
    private AgentToolExecutor agentToolExecutor;

    @Mock
    private ConversationMemoryManager conversationMemoryManager;

    private ToolCallProcessor toolCallProcessor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        toolCallProcessor = new ToolCallProcessor(agentToolExecutor, conversationMemoryManager);
    }

    @Test
    void processShouldContinueWhenOtherToolsExecuteSuccessfully() {
        SuperAgentContext context = new SuperAgentContext();
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "meeting_tool", "{}")))
                .build();
        ToolResponseMessage responseMessage = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "meeting_tool", "done")))
                .build();
        when(agentToolExecutor.execute(any(Prompt.class), any(AssistantMessage.class))).thenReturn(responseMessage);

        ToolCallProcessingResult result = toolCallProcessor.process(new Prompt(List.of()), assistantMessage, context,
                SuperAgentContext.Stage.EXECUTION);

        verify(conversationMemoryManager).appendDialogueMessage(context, responseMessage);
        assertFalse(result.directAnswerTriggered());
    }

    @Test
    void processShouldAppendErrorWhenOtherToolFails() {
        SuperAgentContext context = new SuperAgentContext();
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "meeting_tool", "{}")))
                .build();
        doThrow(new IllegalStateException("boom")).when(agentToolExecutor)
                .execute(any(Prompt.class), any(AssistantMessage.class));

        toolCallProcessor.process(new Prompt(List.of()), assistantMessage, context, SuperAgentContext.Stage.EXECUTION);

        verify(conversationMemoryManager).appendDialogueMessage(any(), any(ToolResponseMessage.class));
    }

    @Test
    void processShouldSuspendWhenAskHumanIsRequested() {
        SuperAgentContext context = new SuperAgentContext();
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        context.setSseEmitter(emitter);
        String arguments = "{\"arg0\":\"[{\\\"question\\\":\\\"Need input?\\\",\\\"interactionType\\\":\\\"TEXT_INPUT\\\"}]\"}";
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "ask_human", arguments)))
                .build();

        assertThrows(HumanInTheLoopException.class,
                () -> toolCallProcessor.process(new Prompt(List.of()), assistantMessage, context,
                        SuperAgentContext.Stage.EXECUTION));

        assertTrue(emitter.payloads.stream().anyMatch(payload -> payload.contains("ASK_HUMAN")));
    }

    private static class CapturingSseEmitter extends SseEmitter {
        private final List<String> payloads = new ArrayList<>();

        @Override
        public synchronized void send(Object object) throws IOException {
            payloads.add(String.valueOf(object));
        }
    }
}
