package org.gemo.apex.component;

import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.core.engine.ToolExecutionOutcome;
import org.gemo.apex.exception.HumanInTheLoopException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomToolCallingManagerTest {

    @Test
    void executeToolCallsShouldReturnFullConversationHistoryAndNotify() {
        ToolInvocationNotifier notifier = Mockito.mock(ToolInvocationNotifier.class);
        CustomToolCallingManager manager = CustomToolCallingManager.builder()
                .toolInvocationNotifier(notifier)
                .build();

        ToolCallback toolCallback = Mockito.mock(ToolCallback.class);
        ToolDefinition definition = DefaultToolDefinition.builder()
                .name("meeting_tool")
                .description("meeting")
                .inputSchema("{}")
                .build();
        when(toolCallback.getToolDefinition()).thenReturn(definition);
        when(toolCallback.getToolMetadata()).thenReturn(ToolMetadata.builder().returnDirect(false).build());
        when(toolCallback.call(any(String.class), any())).thenReturn("tool-result");

        ToolCallingChatOptions chatOptions = Mockito.mock(ToolCallingChatOptions.class);
        when(chatOptions.getToolCallbacks()).thenReturn(List.of(toolCallback));
        when(chatOptions.getToolNames()).thenReturn(Set.of());
        when(chatOptions.getToolContext()).thenReturn(Map.of(
                ToolContextKeys.INVOCATION_ID, "invocation-main"));

        Prompt prompt = new Prompt(List.of(new UserMessage("hello")), chatOptions);
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "meeting_tool", "{}")))
                .build();

        ToolExecutionResult result = manager.executeToolCalls(prompt,
                new org.springframework.ai.chat.model.ChatResponse(
                        List.of(new org.springframework.ai.chat.model.Generation(assistantMessage))));

        assertEquals(3, result.conversationHistory().size());
        assertEquals(UserMessage.class, result.conversationHistory().get(0).getClass());
        assertEquals(AssistantMessage.class, result.conversationHistory().get(1).getClass());
        assertEquals(ToolResponseMessage.class, result.conversationHistory().get(2).getClass());
        assertFalse(result.returnDirect());
        verify(notifier, times(1)).beforeExecution(any(), any(), eq("invocation-main"));
        verify(notifier, times(1)).afterExecution(any(), any(), eq("invocation-main"));
    }

    @Test
    void executeToolCallsShouldLeaveLifecycleHooksToAgentLoop() {
        ToolInvocationNotifier notifier = Mockito.mock(ToolInvocationNotifier.class);
        CustomToolCallingManager manager = CustomToolCallingManager.builder()
                .toolInvocationNotifier(notifier)
                .build();

        ToolCallback toolCallback = Mockito.mock(ToolCallback.class);
        ToolDefinition definition = DefaultToolDefinition.builder()
                .name("meeting_tool")
                .description("meeting")
                .inputSchema("{}")
                .build();
        when(toolCallback.getToolDefinition()).thenReturn(definition);
        when(toolCallback.getToolMetadata()).thenReturn(ToolMetadata.builder().returnDirect(false).build());
        when(toolCallback.call(any(String.class), any())).thenReturn("should-not-run");

        SuperAgentContext sessionContext = new SuperAgentContext();
        sessionContext.setAgentKey("default_agent");
        sessionContext.setSessionId("session-1");
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        sessionContext.setSseEmitter(emitter);

        ToolCallingChatOptions chatOptions = Mockito.mock(ToolCallingChatOptions.class);
        when(chatOptions.getToolCallbacks()).thenReturn(List.of(toolCallback));
        when(chatOptions.getToolNames()).thenReturn(Set.of());
        when(chatOptions.getToolContext()).thenReturn(Map.of(ToolContextKeys.SESSION_CONTEXT, sessionContext));

        Prompt prompt = new Prompt(List.of(new UserMessage("hello")), chatOptions);
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "meeting_tool", "{}")))
                .build();

        ToolExecutionResult result = manager.executeToolCalls(
                prompt,
                new ChatResponse(List.of(new Generation(assistantMessage))));

        verify(toolCallback).call(any(String.class), any());
        ToolResponseMessage response = (ToolResponseMessage) result.conversationHistory().get(2);
        assertEquals("should-not-run", response.getResponses().getFirst().responseData());
    }

    @Test
    void executeToolCallsShouldExposeConvertedToolFailureOutcome() {
        CustomToolCallingManager manager = CustomToolCallingManager.builder()
                .toolInvocationNotifier(Mockito.mock(ToolInvocationNotifier.class))
                .toolExecutionExceptionProcessor(exception -> "converted failure")
                .build();
        ToolCallback toolCallback = Mockito.mock(ToolCallback.class);
        ToolDefinition definition = DefaultToolDefinition.builder()
                .name("meeting_tool")
                .description("meeting")
                .inputSchema("{}")
                .build();
        when(toolCallback.getToolDefinition()).thenReturn(definition);
        when(toolCallback.getToolMetadata()).thenReturn(ToolMetadata.builder().returnDirect(false).build());
        when(toolCallback.call(any(String.class), any()))
                .thenThrow(new ToolExecutionException(definition, new IllegalStateException("boom")));
        ToolExecutionOutcome outcome = new ToolExecutionOutcome();
        ToolCallingChatOptions chatOptions = Mockito.mock(ToolCallingChatOptions.class);
        when(chatOptions.getToolCallbacks()).thenReturn(List.of(toolCallback));
        when(chatOptions.getToolNames()).thenReturn(Set.of());
        when(chatOptions.getToolContext()).thenReturn(Map.of(
                ToolContextKeys.EXECUTION_OUTCOME, outcome,
                ToolContextKeys.INVOCATION_ID, "invocation-failed"));
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "meeting_tool", "{}")))
                .build();

        ToolExecutionResult result = manager.executeToolCalls(
                new Prompt(List.of(new UserMessage("hello")), chatOptions),
                new ChatResponse(List.of(new Generation(assistantMessage))));

        assertFalse(outcome.isSucceeded());
        assertEquals("converted failure",
                ((ToolResponseMessage) result.conversationHistory().getLast())
                        .getResponses().getFirst().responseData());
    }

    @Test
    void executeToolCallsShouldReturnRawResultWithoutLifecyclePostProcessing() {
        ToolInvocationNotifier notifier = Mockito.mock(ToolInvocationNotifier.class);
        CustomToolCallingManager manager = CustomToolCallingManager.builder()
                .toolInvocationNotifier(notifier)
                .build();

        ToolCallback toolCallback = Mockito.mock(ToolCallback.class);
        ToolDefinition definition = DefaultToolDefinition.builder()
                .name("activate_skill")
                .description("skill")
                .inputSchema("{}")
                .build();
        when(toolCallback.getToolDefinition()).thenReturn(definition);
        when(toolCallback.getToolMetadata()).thenReturn(ToolMetadata.builder().returnDirect(false).build());
        when(toolCallback.call(any(String.class), any())).thenReturn("""
                <activated_skill name="writing-plans">
                  <instructions>
                    body
                  </instructions>
                </activated_skill>
                """);

        SuperAgentContext sessionContext = new SuperAgentContext();
        sessionContext.setAgentKey("default_agent");
        sessionContext.setSessionId("session-1");

        ToolCallingChatOptions chatOptions = Mockito.mock(ToolCallingChatOptions.class);
        when(chatOptions.getToolCallbacks()).thenReturn(List.of(toolCallback));
        when(chatOptions.getToolNames()).thenReturn(Set.of());
        when(chatOptions.getToolContext()).thenReturn(Map.of(ToolContextKeys.SESSION_CONTEXT, sessionContext));

        Prompt prompt = new Prompt(List.of(new UserMessage("hello")), chatOptions);
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "activate_skill",
                        "{\"command\":\"writing-plans\"}")))
                .build();

        ToolExecutionResult result = manager.executeToolCalls(prompt, new ChatResponse(List.of(new Generation(assistantMessage))));

        ToolResponseMessage response = (ToolResponseMessage) result.conversationHistory().get(2);
        assertTrue(response.getResponses().getFirst().responseData().contains("<activated_skill"));
    }

    private static class CapturingSseEmitter extends SseEmitter {
        private final List<String> payloads = new ArrayList<>();

        @Override
        public synchronized void send(Object object) throws IOException {
            payloads.add(String.valueOf(object));
        }
    }
}
