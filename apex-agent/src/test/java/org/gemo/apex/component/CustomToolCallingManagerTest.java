package org.gemo.apex.component;

import org.gemo.apex.context.SuperAgentContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
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
        when(chatOptions.getToolContext()).thenReturn(Map.of());

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
        verify(notifier, times(1)).beforeExecution(any(), any(), any());
        verify(notifier, times(1)).afterExecution(any(), any(), any());
    }
}
