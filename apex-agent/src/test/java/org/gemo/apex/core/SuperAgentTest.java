package org.gemo.apex.core;

import org.gemo.apex.component.interceptor.ToolInterceptor;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.core.engine.AgentPromptAssembler;
import org.gemo.apex.core.engine.HumanInLoopResumer;
import org.gemo.apex.core.engine.ModelResponseStreamer;
import org.gemo.apex.core.engine.StageToolPlan;
import org.gemo.apex.core.engine.StageToolResolver;
import org.gemo.apex.core.engine.ToolCallProcessingResult;
import org.gemo.apex.core.engine.ToolCallProcessor;
import org.gemo.apex.exception.HumanInTheLoopException;
import org.gemo.apex.hook.lifecycle.AgentRuntimeContext;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.memory.write.MemoryLifecycleManager;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuperAgentTest {

    @Mock
    private HumanInLoopResumer humanInLoopResumer;

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

    @Mock
    private SessionContextStore sessionContextStore;

    @Mock
    private MemoryLifecycleManager memoryLifecycleManager;

    private SuperAgentContext context;
    private SuperAgent superAgent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        context = new SuperAgentContext();
        context.setSessionId("session-1");
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);
        when(stageToolResolver.resolve(any())).thenReturn(new StageToolPlan(List.of(), List.of()));
        when(agentPromptAssembler.assemble(any(), any())).thenReturn(new Prompt(List.of()));
        when(toolInterceptor.interceptIllegalToolCalls(any(), any())).thenReturn(null);
        when(toolCallProcessor.process(any(), any(), any(), any())).thenReturn(ToolCallProcessingResult.continueLoop());
        when(toolCallProcessor.process(any(), any(), any(), any(), any()))
                .thenReturn(ToolCallProcessingResult.continueLoop());
        superAgent = new SuperAgent(
                context,
                humanInLoopResumer,
                stageToolResolver,
                agentPromptAssembler,
                modelResponseStreamer,
                toolInterceptor,
                toolCallProcessor,
                conversationMemoryManager,
                sessionContextStore,
                memoryLifecycleManager);
    }

    @Test
    void runShouldCompleteAndPersistWhenLoopEndsWithoutToolCalls() {
        when(modelResponseStreamer.stream(any(), same(context))).thenReturn(response("done"));

        superAgent.run();

        assertEquals(ExecutionStatus.COMPLETED, context.getExecutionStatus());
        verify(humanInLoopResumer).resume(same(context), any());
        verify(conversationMemoryManager).appendDialogueMessage(any(), any(AssistantMessage.class));
        verify(sessionContextStore).save(context);
        verify(memoryLifecycleManager).onTurnCompleted(context);
    }

    @Test
    void runShouldContinueAfterIllegalToolInterception() {
        AssistantMessage toolCallMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "meeting_tool", "{}")))
                .build();
        ToolResponseMessage interceptResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "meeting_tool", "illegal")))
                .build();

        when(modelResponseStreamer.stream(any(), same(context)))
                .thenReturn(new ChatResponse(List.of(new Generation(toolCallMessage))))
                .thenReturn(response("done"));
        when(toolInterceptor.interceptIllegalToolCalls(any(), any())).thenReturn(interceptResponse).thenReturn(null);

        superAgent.run();

        assertEquals(ExecutionStatus.COMPLETED, context.getExecutionStatus());
        verify(conversationMemoryManager).appendDialogueMessage(any(), any(ToolResponseMessage.class));
        verify(toolCallProcessor, never()).process(any(), any(), any(), any());
    }

    @Test
    void runShouldFinalizeOnceAndSwallowWhenHumanInLoop() {
        doAnswer(invocation -> {
            context.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
            throw new HumanInTheLoopException("waiting");
        }).when(modelResponseStreamer).stream(any(), same(context));

        assertDoesNotThrow(() -> superAgent.run());

        assertEquals(ExecutionStatus.HUMAN_IN_THE_LOOP, context.getExecutionStatus());
        verify(sessionContextStore, times(1)).save(context);
        verify(memoryLifecycleManager, never()).onTurnCompleted(context);
    }

    @Test
    void runShouldMarkFailedAndPersistOnRuntimeException() {
        doThrow(new IllegalStateException("boom")).when(modelResponseStreamer).stream(any(), same(context));

        assertThrows(IllegalStateException.class, () -> superAgent.run());

        assertEquals(ExecutionStatus.FAILED, context.getExecutionStatus());
        verify(sessionContextStore).save(context);
        verify(memoryLifecycleManager).onTurnCompleted(context);
    }

    @Test
    void runShouldResumeOutstandingToolCallsInSameTrace() {
        AssistantMessage toolCallingMessage = AssistantMessage.builder()
                .toolCalls(List.of(
                        new AssistantMessage.ToolCall("call-1", "function", "meeting_tool", "{}"),
                        new AssistantMessage.ToolCall("call-2", "function", "notify_tool", "{}")))
                .build();
        ToolResponseMessage firstResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call-1", "meeting_tool", "approved")))
                .build();
        context.setTurnNo(1L);
        context.setTraceNo(1);
        context.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
        context.setWorkingMessages(new java.util.ArrayList<>(List.of(toolCallingMessage)));
        context.setDialogueMessages(new java.util.ArrayList<>());
        doAnswer(invocation -> {
            AgentRuntimeContext runtime = invocation.getArgument(1);
            runtime.getWorkingMessages().add(firstResponse);
            context.getDialogueMessages().add(firstResponse);
            context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);
            return null;
        }).when(humanInLoopResumer).resume(same(context), any());
        when(agentPromptAssembler.assembleToolExecutionPrompt(same(context), any())).thenReturn(new Prompt(List.of()));
        when(modelResponseStreamer.stream(any(), same(context))).thenReturn(response("done"));

        superAgent.run();

        verify(toolCallProcessor).process(
                any(),
                argThat(message -> message.getToolCalls().size() == 1
                        && "call-2".equals(message.getToolCalls().getFirst().id())),
                same(context),
                same(context.getCurrentStage()),
                any());
        assertEquals(ExecutionStatus.COMPLETED, context.getExecutionStatus());
    }

    @Test
    void runShouldResetActiveSkillsAfterTurnCompletion() {
        context.setActiveSkillNames(new java.util.ArrayList<>(List.of("meeting-skill")));
        when(modelResponseStreamer.stream(any(), same(context))).thenReturn(response("done"));

        superAgent.run();

        assertEquals(List.of(), context.getActiveSkillNames());
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
