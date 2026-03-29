package org.gemo.apex.core.engine;

import org.gemo.apex.component.interceptor.ToolInterceptor;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuperAgentLoopRunnerTest {

    @Mock
    private StageToolResolver stageToolResolver;

    @Mock
    private AgentPromptAssembler agentPromptAssembler;

    @Mock
    private ModelResponseStreamer modelResponseStreamer;

    @Mock
    private ToolInterceptor toolInterceptor;

    @Mock
    private ToolCallProcessor toolCallProcessor;

    @Mock
    private ConversationMemoryManager conversationMemoryManager;

    private SuperAgentLoopRunner superAgentLoopRunner;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        superAgentLoopRunner = new SuperAgentLoopRunner(stageToolResolver, agentPromptAssembler, modelResponseStreamer,
                toolInterceptor, toolCallProcessor, conversationMemoryManager);
        when(stageToolResolver.resolve(any())).thenReturn(new StageToolPlan(List.of(), List.of()));
        when(agentPromptAssembler.assemble(any(), any())).thenReturn(new Prompt(List.of()));
        when(toolInterceptor.interceptIllegalToolCalls(any(), any())).thenReturn(null);
        when(toolCallProcessor.process(any(), any(), any(), any())).thenReturn(ToolCallProcessingResult.continueLoop());
    }

    @Test
    void runShouldTransitionThinkingToModeConfirmationAndContinue() {
        SuperAgentContext context = new SuperAgentContext();
        context.setCurrentStage(SuperAgentContext.Stage.THINKING);
        context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);

        when(modelResponseStreamer.stream(any(), any()))
                .thenReturn(response("thinking"))
                .thenReturn(response("confirm"));

        superAgentLoopRunner.run(context);

        assertEquals(SuperAgentContext.Stage.MODE_CONFIRMATION, context.getCurrentStage());
        verify(modelResponseStreamer, times(2)).stream(any(), any());
        verify(conversationMemoryManager, times(1)).appendDialogueMessage(any(), any(AssistantMessage.class));
    }

    @Test
    void runShouldStopOnExecutionWithoutToolCalls() {
        SuperAgentContext context = new SuperAgentContext();
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);

        when(modelResponseStreamer.stream(any(), any())).thenReturn(response("done"));

        superAgentLoopRunner.run(context);

        verify(modelResponseStreamer, times(1)).stream(any(), any());
        assertEquals(ExecutionStatus.COMPLETED, context.getExecutionStatus());
    }

    @Test
    void runShouldContinueAfterIllegalToolInterception() {
        SuperAgentContext context = new SuperAgentContext();
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);

        AssistantMessage toolCallMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "meeting_tool", "{}")))
                .build();
        ToolResponseMessage interceptResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "meeting_tool", "illegal")))
                .build();

        when(modelResponseStreamer.stream(any(), any()))
                .thenReturn(new ChatResponse(List.of(new Generation(toolCallMessage))))
                .thenReturn(response("done"));
        when(toolInterceptor.interceptIllegalToolCalls(any(), any())).thenReturn(interceptResponse).thenReturn(null);

        superAgentLoopRunner.run(context);

        verify(conversationMemoryManager, times(1)).appendDialogueMessage(any(), any(ToolResponseMessage.class));
        verify(toolCallProcessor, never()).process(any(), any(), any(), any());
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
