package org.gemo.apex.core.engine;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.config.model.HookBindingConfig;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.definition.agent.AgentDefinition;
import org.gemo.apex.exception.HumanInTheLoopException;
import org.gemo.apex.hook.ToolMatcher;
import org.gemo.apex.hook.lifecycle.AgentLifecycleHookRuntime;
import org.gemo.apex.hook.lifecycle.AgentRuntimeContext;
import org.gemo.apex.hook.lifecycle.AgentTrace;
import org.gemo.apex.hook.lifecycle.AgentTurn;
import org.gemo.apex.hook.lifecycle.DefaultAgentLifecycleHookRuntime;
import org.gemo.apex.hook.lifecycle.HookDispatchResult;
import org.gemo.apex.hook.lifecycle.HookFlowAction;
import org.gemo.apex.hook.tool.PostToolCallHook;
import org.gemo.apex.hook.tool.PostToolCallHookContext;
import org.gemo.apex.hook.tool.PostToolCallHookResult;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.gemo.apex.tool.metadata.CustomToolMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void processShouldAppendCompletedResponsesBeforeLaterToolSuspends() {
        SuperAgentContext context = new SuperAgentContext();
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);

        AssistantMessage.ToolCall firstToolCall =
                new AssistantMessage.ToolCall("call-1", "function", "contacts_tool", "{}");
        AssistantMessage.ToolCall secondToolCall =
                new AssistantMessage.ToolCall("call-2", "function", "meeting_tool", "{}");

        when(agentToolExecutor.execute(any(Prompt.class), argThat(message ->
                message.getToolCalls().size() == 1
                        && "contacts_tool".equals(message.getToolCalls().getFirst().name()))))
                .thenReturn(ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "contacts_tool", "done")))
                        .build());

        when(agentToolExecutor.execute(any(Prompt.class), argThat(message ->
                message.getToolCalls().size() == 1
                        && "meeting_tool".equals(message.getToolCalls().getFirst().name()))))
                .thenThrow(new HumanInTheLoopException("waiting for confirmation"));

        assertThrows(HumanInTheLoopException.class,
                () -> toolCallProcessor.process(new Prompt(List.of()),
                        AssistantMessage.builder().toolCalls(List.of(firstToolCall, secondToolCall)).build(),
                        context,
                        SuperAgentContext.Stage.EXECUTION));

        verify(conversationMemoryManager).appendDialogueMessage(eq(context), argThat((ToolResponseMessage response) ->
                response.getResponses().size() == 1
                        && "call-1".equals(response.getResponses().getFirst().id())));
    }

    @Test
    void lifecycleShouldRejectToolRemovedFromEnabledTools() {
        ToolCallback tool = tool("meeting_tool", "meeting description", "MCP");
        AgentLifecycleHookRuntime hooks = (point, runtime, skipped) -> HookDispatchResult.continued();
        ToolCallProcessor processor = new ToolCallProcessor(agentToolExecutor, conversationMemoryManager, hooks);
        AgentRuntimeContext runtime = runtime(List.of(tool), List.of(), AgentHooksConfig.empty());
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-disabled", "function", "meeting_tool", "{}")))
                .build();

        processor.process(new Prompt(List.of()), assistantMessage, runtime.getSessionContext(),
                SuperAgentContext.Stage.EXECUTION, runtime);

        verify(agentToolExecutor, never()).execute(any(), any());
        assertEquals(HookFlowAction.BLOCK_TOOL, runtime.getTrace().getToolCalls().getFirst().getAction());
        verify(conversationMemoryManager).appendDialogueMessage(any(), argThat((ToolResponseMessage response) ->
                response.getResponses().getFirst().responseData().contains("tool disabled by lifecycle hook")));
    }

    @Test
    void legacyPostHookShouldReceiveCompleteFailedInvocationContext() {
        ToolCallback tool = tool("meeting_tool", "meeting description", "MCP");
        AtomicReference<PostToolCallHookContext> captured = new AtomicReference<>();
        PostToolCallHook postHook = hookContext -> {
            captured.set(hookContext);
            return PostToolCallHookResult.replaceResult("handled failure");
        };
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean("legacyPost")).thenReturn(postHook);
        AgentHooksConfig hooksConfig = AgentHooksConfig.builder()
                .postToolCall(List.of(HookBindingConfig.builder().bean("legacyPost").order(1).build()))
                .build();
        DefaultAgentLifecycleHookRuntime hooks =
                new DefaultAgentLifecycleHookRuntime(applicationContext, new ToolMatcher());
        ToolCallProcessor processor = new ToolCallProcessor(agentToolExecutor, conversationMemoryManager, hooks);
        AgentRuntimeContext runtime = runtime(List.of(tool), List.of(tool), hooksConfig);
        doThrow(new IllegalStateException("boom")).when(agentToolExecutor).execute(any(), any());
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-failed", "function", "meeting_tool", "{\"room\":\"A1001\"}")))
                .build();

        processor.process(new Prompt(List.of()), assistantMessage, runtime.getSessionContext(),
                SuperAgentContext.Stage.EXECUTION, runtime);

        PostToolCallHookContext hookContext = captured.get();
        assertNotNull(hookContext);
        assertNotNull(hookContext.getInvocationId());
        assertFalse(hookContext.getInvocationId().isBlank());
        assertEquals("meeting description", hookContext.getToolDescription());
        assertEquals("MCP", hookContext.getToolType());
        assertFalse(hookContext.isToolExecutionSucceeded());
        assertEquals(hookContext.getInvocationId(),
                runtime.getTrace().getToolCalls().getFirst().getInvocationId());
        assertFalse(runtime.getTrace().getToolCalls().getFirst().isSucceeded());
        verify(conversationMemoryManager).appendDialogueMessage(any(), argThat((ToolResponseMessage response) ->
                "handled failure".equals(response.getResponses().getFirst().responseData())));
    }

    private AgentRuntimeContext runtime(List<ToolCallback> availableTools, List<ToolCallback> enabledTools,
            AgentHooksConfig hooks) {
        SuperAgentContext context = new SuperAgentContext();
        context.setAgentKey("agent-1");
        context.setSessionId("session-1");
        context.setUserId("user-1");
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        return AgentRuntimeContext.builder()
                .sessionContext(context)
                .agentDefinition(new AgentDefinition(
                        "agent-1", ModeEnum.REACT, List.of(), List.of(), List.of(),
                        hooks, "", "", "", ""))
                .turn(AgentTurn.builder().turnNo(1L).build())
                .trace(AgentTrace.builder().turnNo(1L).traceNo(1).build())
                .availableTools(new ArrayList<>(availableTools))
                .enabledTools(new ArrayList<>(enabledTools))
                .workingMessages(new ArrayList<>())
                .build();
    }

    private ToolCallback tool(String name, String description, String type) {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(DefaultToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema("{}")
                .build());
        when(callback.getToolMetadata()).thenReturn(CustomToolMetadata.builder().type(type).build());
        return callback;
    }

    private static class CapturingSseEmitter extends SseEmitter {
        private final List<String> payloads = new ArrayList<>();

        @Override
        public synchronized void send(Object object) throws IOException {
            payloads.add(String.valueOf(object));
        }
    }
}
