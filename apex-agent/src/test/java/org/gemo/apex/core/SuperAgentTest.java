package org.gemo.apex.core;

import org.gemo.apex.component.interceptor.ToolInterceptor;
import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.config.model.HookBindingConfig;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.core.engine.AgentPromptAssembler;
import org.gemo.apex.core.engine.HumanInLoopResumer;
import org.gemo.apex.core.engine.ModelResponseStreamer;
import org.gemo.apex.core.engine.StageToolPlan;
import org.gemo.apex.core.engine.StageToolResolver;
import org.gemo.apex.core.engine.ToolCallProcessingResult;
import org.gemo.apex.core.engine.ToolCallProcessor;
import org.gemo.apex.exception.HumanInTheLoopException;
import org.gemo.apex.definition.agent.AgentDefinition;
import org.gemo.apex.hook.ToolMatcher;
import org.gemo.apex.hook.lifecycle.AgentLifecycleHook;
import org.gemo.apex.hook.lifecycle.AgentLifecycleHookRuntime;
import org.gemo.apex.hook.lifecycle.AgentRuntimeContext;
import org.gemo.apex.hook.lifecycle.DefaultAgentLifecycleHookRuntime;
import org.gemo.apex.hook.lifecycle.HookDispatchResult;
import org.gemo.apex.hook.lifecycle.HookPoint;
import org.gemo.apex.hook.lifecycle.InMemoryAgentExecutionStore;
import org.gemo.apex.hook.lifecycle.MessageOperation;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.memory.write.MemoryLifecycleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
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
import static org.mockito.Mockito.mock;
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
    void runShouldResumeOutstandingToolCallsInSameIteration() {
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
        context.setIterationNo(1);
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

    @Test
    void runShouldRefreshFixedPromptAfterStageSwitchAndRetainWorkingDialogue() {
        StageToolPlan writePlan = new StageToolPlan(List.of(), List.of());
        StageToolPlan executePlan = new StageToolPlan(List.of(), List.of());
        when(stageToolResolver.resolve(any()))
                .thenReturn(writePlan)
                .thenReturn(writePlan)
                .thenReturn(executePlan);
        when(agentPromptAssembler.prepareWorkingMessages(same(context), any())).thenAnswer(invocation -> {
            SystemMessage fixed = new SystemMessage("write-plan");
            context.setFixedMessages(new ArrayList<>(List.of(fixed)));
            return new ArrayList<>(List.of(fixed, new UserMessage("original-dialogue")));
        });
        when(agentPromptAssembler.refreshFixedMessages(same(context), any())).thenAnswer(invocation -> {
            StageToolPlan plan = invocation.getArgument(1);
            SystemMessage fixed = new SystemMessage(plan == executePlan ? "execute-plan" : "write-plan");
            context.setFixedMessages(new ArrayList<>(List.of(fixed)));
            return new ArrayList<>(List.of(fixed));
        });
        when(agentPromptAssembler.assemble(same(context), any(), any(), any())).thenAnswer(invocation ->
                new Prompt(new ArrayList<>(invocation.<List<Message>>getArgument(2))));
        AssistantMessage toolCallMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "write_plan", "{}")))
                .build();
        when(modelResponseStreamer.stream(any(), same(context)))
                .thenReturn(new ChatResponse(List.of(new Generation(toolCallMessage))))
                .thenReturn(response("done"));
        when(toolCallProcessor.process(any(), any(), same(context), any(), any())).thenAnswer(invocation -> {
            AgentRuntimeContext runtime = invocation.getArgument(4);
            runtime.getWorkingMessages().add(new UserMessage("hook-dialogue"));
            return ToolCallProcessingResult.continueLoop();
        });

        superAgent.run();

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(modelResponseStreamer, times(2)).stream(promptCaptor.capture(), same(context));
        assertEquals("write-plan", promptCaptor.getAllValues().get(0).getInstructions().getFirst().getText());
        assertEquals("execute-plan", promptCaptor.getAllValues().get(1).getInstructions().getFirst().getText());
        assertEquals("hook-dialogue",
                promptCaptor.getAllValues().get(1).getInstructions().stream()
                        .filter(message -> "hook-dialogue".equals(message.getText()))
                        .findFirst()
                        .orElseThrow()
                        .getText());
    }

    @Test
    void runShouldPersistPostModelReplacementInsteadOfRawOutput() {
        AssistantMessage replacement = new AssistantMessage("replacement");
        AgentLifecycleHook replaceHook = hookContext -> {
            int lastIndex = hookContext.getRuntimeContext().getWorkingMessages().size() - 1;
            return org.gemo.apex.hook.lifecycle.AgentHookResult.continueWithMessages(
                    List.of(MessageOperation.replace(lastIndex, replacement)));
        };
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean("replaceModelOutput")).thenReturn(replaceHook);
        AgentHooksConfig hookConfig = AgentHooksConfig.builder()
                .postModelCall(List.of(HookBindingConfig.builder()
                        .bean("replaceModelOutput")
                        .order(1)
                        .build()))
                .build();
        DefaultAgentLifecycleHookRuntime hooks =
                new DefaultAgentLifecycleHookRuntime(applicationContext, new ToolMatcher());
        doAnswer(invocation -> {
            context.addMessage(invocation.getArgument(1));
            return null;
        }).when(conversationMemoryManager).appendDialogueMessage(same(context), any());
        when(modelResponseStreamer.stream(any(), same(context))).thenReturn(response("raw"));

        newSuperAgent(hooks, hookConfig).run();

        verify(conversationMemoryManager).appendDialogueMessage(same(context), same(replacement));
        verify(sessionContextStore).appendDialogueMessages(
                same(context.getSessionId()),
                same(context.getTurnNo()),
                any(),
                argThat(messages -> messages.size() == 1 && messages.getFirst() == replacement));
    }

    @Test
    void runShouldAssemblePromptWithToolsModifiedByPreModelHook() {
        ToolCallback tool = mock(ToolCallback.class);
        when(tool.getToolDefinition()).thenReturn(DefaultToolDefinition.builder()
                .name("meeting_tool")
                .description("meeting")
                .inputSchema("{}")
                .build());
        context.setAvailableTools(new ArrayList<>(List.of(tool)));
        when(stageToolResolver.resolve(any())).thenReturn(new StageToolPlan(List.of(tool), List.of(tool)));
        AgentLifecycleHookRuntime hooks = (point, runtime, skipped) -> {
            if (point == HookPoint.PRE_MODEL_CALL) {
                runtime.setEnabledTools(new ArrayList<>());
            }
            return HookDispatchResult.continued();
        };
        when(agentPromptAssembler.assemble(same(context), any(), any(), any())).thenReturn(new Prompt(List.of()));
        when(modelResponseStreamer.stream(any(), same(context))).thenReturn(response("done"));

        newSuperAgent(hooks, AgentHooksConfig.empty()).run();

        verify(agentPromptAssembler).assemble(
                same(context),
                any(),
                any(),
                argThat(List::isEmpty));
    }

    private SuperAgent newSuperAgent(AgentLifecycleHookRuntime hooks, AgentHooksConfig hookConfig) {
        context.setAgentKey("agent-1");
        return new SuperAgent(
                context,
                humanInLoopResumer,
                stageToolResolver,
                agentPromptAssembler,
                modelResponseStreamer,
                toolInterceptor,
                toolCallProcessor,
                conversationMemoryManager,
                sessionContextStore,
                memoryLifecycleManager,
                agentKey -> new AgentDefinition(
                        agentKey, ModeEnum.REACT, List.of(), List.of(), List.of(),
                        hookConfig, "", "", "", ""),
                hooks,
                new InMemoryAgentExecutionStore());
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
