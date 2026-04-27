package org.gemo.apex.core.engine;

import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.domain.interaction.InteractionType;
import org.gemo.apex.domain.interaction.PendingHumanInteraction;
import org.gemo.apex.domain.interaction.PendingToolExecution;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HumanInLoopResumerTest {

    @Mock
    private ConversationMemoryManager conversationMemoryManager;

    @Mock
    private AgentToolExecutor agentToolExecutor;

    @Mock
    private AgentPromptAssembler agentPromptAssembler;

    private HumanInLoopResumer humanInLoopResumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        humanInLoopResumer = new HumanInLoopResumer(conversationMemoryManager, agentToolExecutor, agentPromptAssembler);
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

    @Test
    void resumeShouldExecutePendingToolAfterApprovalWithoutReTriggeringSameHook() {
        SuperAgentContext context = new SuperAgentContext();
        context.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
        context.setAgentKey("default_agent");
        context.setSessionId("session-1");
        context.setMessages(new java.util.ArrayList<>(List.of(
                AssistantMessage.builder()
                        .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "meeting_tool", "{}")))
                        .build())));
        context.setPendingHumanInteraction(PendingHumanInteraction.builder()
                .interactionType(InteractionType.TOOL_CONFIRMATION.name())
                .toolCallId("call-1")
                .invocationId("invocation-1")
                .confirmationId("confirm-1")
                .build());
        context.setPendingToolExecution(PendingToolExecution.builder()
                .toolCallId("call-1")
                .toolName("meeting_tool")
                .invocationId("invocation-1")
                .resolvedArguments(Map.of("room", "A1001", "date", "2026-04-22"))
                .editableFieldKeys(List.of("room"))
                .confirmationId("confirm-1")
                .executedPreHookBeans(List.of("mutateRoomHook", "toolConfirmHook"))
                .build());
        context.setPendingToolResult(Map.of("call-1", Map.of(
                "interaction_type", "TOOL_CONFIRMATION",
                "confirmation_id", "confirm-1",
                "decision", "APPROVE",
                "updated_args", Map.of("room", "B2001"))));

        Prompt prompt = new Prompt(List.of());
        when(agentPromptAssembler.assembleToolExecutionPrompt(eq(context), anyMap())).thenReturn(prompt);
        when(agentToolExecutor.execute(eq(prompt), any(AssistantMessage.class))).thenReturn(
                ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "meeting_tool", "approved")))
                        .build());

        humanInLoopResumer.resume(context);

        verify(agentPromptAssembler).assembleToolExecutionPrompt(eq(context), argThat(extra ->
                List.of("mutateRoomHook", "toolConfirmHook").equals(extra.get(ToolContextKeys.SKIP_PRE_HOOK_BEANS))));
        verify(agentToolExecutor).execute(eq(prompt), argThat(message ->
                message.getToolCalls().size() == 1
                        && "meeting_tool".equals(message.getToolCalls().getFirst().name())
                        && message.getToolCalls().getFirst().arguments().contains("B2001")));
        verify(conversationMemoryManager).appendDialogueMessage(eq(context), any(ToolResponseMessage.class));
        assertNull(context.getPendingHumanInteraction());
        assertNull(context.getPendingToolExecution());
        assertNull(context.getPendingToolResult());
        assertEquals(ExecutionStatus.IN_PROGRESS, context.getExecutionStatus());
    }
}
