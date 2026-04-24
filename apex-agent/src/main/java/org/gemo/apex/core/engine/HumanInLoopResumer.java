package org.gemo.apex.core.engine;

import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.domain.interaction.InteractionType;
import org.gemo.apex.domain.interaction.PendingHumanInteraction;
import org.gemo.apex.domain.interaction.PendingToolExecution;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.gemo.apex.util.JacksonUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class HumanInLoopResumer {

    private final ConversationMemoryManager conversationMemoryManager;
    private final AgentToolExecutor agentToolExecutor;
    private final AgentPromptAssembler agentPromptAssembler;

    public HumanInLoopResumer(ConversationMemoryManager conversationMemoryManager,
            AgentToolExecutor agentToolExecutor,
            AgentPromptAssembler agentPromptAssembler) {
        this.conversationMemoryManager = conversationMemoryManager;
        this.agentToolExecutor = agentToolExecutor;
        this.agentPromptAssembler = agentPromptAssembler;
    }

    public void resume(SuperAgentContext context) {
        if (context.getExecutionStatus() != ExecutionStatus.HUMAN_IN_THE_LOOP) {
            return;
        }

        if (isToolConfirmation(context.getPendingHumanInteraction(), context.getPendingToolExecution())) {
            resumeToolConfirmation(context);
            return;
        }

        if (CollectionUtils.isEmpty(context.getMessages())) {
            return;
        }

        resumeAskHuman(context);
    }

    private void resumeAskHuman(SuperAgentContext context) {
        List<Message> suspendedHistory = context.getMessages();
        Map<String, Object> pendingResult = context.getPendingToolResult() != null
                ? context.getPendingToolResult()
                : new HashMap<>();

        AssistantMessage lastAssistantMessage = null;
        for (int i = suspendedHistory.size() - 1; i >= 0; i--) {
            if (suspendedHistory.get(i) instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
                lastAssistantMessage = assistantMessage;
                break;
            }
        }

        if (lastAssistantMessage != null && lastAssistantMessage.hasToolCalls()) {
            List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
            if (!context.getMessages().isEmpty()
                    && context.getMessages().get(context.getMessages().size() - 1) instanceof ToolResponseMessage) {
                ToolResponseMessage existingToolResponse =
                        (ToolResponseMessage) context.getMessages().get(context.getMessages().size() - 1);
                toolResponses.addAll(existingToolResponse.getResponses());
            }

            for (AssistantMessage.ToolCall toolCall : lastAssistantMessage.getToolCalls()) {
                boolean alreadyResponded = toolResponses.stream().anyMatch(resp -> resp.id().equals(toolCall.id()));
                if (alreadyResponded) {
                    continue;
                }
                if (ToolNames.ASK_HUMAN.equals(toolCall.name())) {
                    String responseData = pendingResult.containsKey(toolCall.id())
                            ? String.valueOf(pendingResult.get(toolCall.id()))
                            : "user did not provide input";
                    toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), responseData));
                }
            }

            if (!toolResponses.isEmpty()) {
                List<ToolResponseMessage.ToolResponse> missingResponses = toolResponses.stream()
                        .filter(resp -> context.getMessages().isEmpty()
                                || !(context.getMessages().get(context.getMessages().size() - 1)
                                        instanceof ToolResponseMessage)
                                || ((ToolResponseMessage) context.getMessages().get(context.getMessages().size() - 1))
                                        .getResponses().stream()
                                        .noneMatch(existing -> existing.id().equals(resp.id())))
                        .toList();
                if (missingResponses.isEmpty()) {
                    context.setPendingToolResult(null);
                    context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);
                    return;
                }
                conversationMemoryManager.appendDialogueMessage(context,
                        ToolResponseMessage.builder().responses(missingResponses).build());
            }
        }

        context.setPendingToolResult(null);
        context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);
    }

    private void resumeToolConfirmation(SuperAgentContext context) {
        PendingToolExecution pendingExecution = context.getPendingToolExecution();
        Map<String, Object> submission = extractSubmission(context.getPendingToolResult(), pendingExecution.getToolCallId());
        String decision = String.valueOf(submission.getOrDefault("decision", "DENY"));

        if ("APPROVE".equalsIgnoreCase(decision)) {
            LinkedHashMap<String, Object> mergedArguments = new LinkedHashMap<>();
            if (pendingExecution.getResolvedArguments() != null) {
                mergedArguments.putAll(pendingExecution.getResolvedArguments());
            }

            mergeEditableOverrides(mergedArguments,
                    extractMap(submission.get("updated_args")),
                    pendingExecution.getEditableFieldKeys() != null
                            ? Set.copyOf(pendingExecution.getEditableFieldKeys())
                            : Set.of());

            Prompt prompt = agentPromptAssembler.assembleToolExecutionPrompt(context, Map.of(
                    ToolContextKeys.SKIP_PRE_HOOK_BEANS, List.of(pendingExecution.getHookSource())));

            AssistantMessage assistantMessage = AssistantMessage.builder()
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                            pendingExecution.getToolCallId(),
                            "function",
                            pendingExecution.getToolName(),
                            JacksonUtils.toJson(mergedArguments))))
                    .build();

            ToolResponseMessage responseMessage = agentToolExecutor.execute(prompt, assistantMessage);
            conversationMemoryManager.appendDialogueMessage(context, responseMessage);
        } else {
            conversationMemoryManager.appendDialogueMessage(context,
                    ToolResponseMessage.builder()
                            .responses(List.of(new ToolResponseMessage.ToolResponse(
                                    pendingExecution.getToolCallId(),
                                    pendingExecution.getToolName(),
                                    "tool execution cancelled by user")))
                            .build());
        }

        context.setPendingHumanInteraction(null);
        context.setPendingToolExecution(null);
        context.setPendingToolResult(null);
        context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);
    }

    private boolean isToolConfirmation(PendingHumanInteraction interaction, PendingToolExecution pendingExecution) {
        return interaction != null
                && pendingExecution != null
                && InteractionType.TOOL_CONFIRMATION.name().equals(interaction.getInteractionType());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSubmission(Map<String, Object> pendingToolResult, String toolCallId) {
        if (pendingToolResult == null || toolCallId == null) {
            return Map.of();
        }
        Object nested = pendingToolResult.get(toolCallId);
        if (nested instanceof Map<?, ?> nestedMap) {
            return (Map<String, Object>) nestedMap;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Object value) {
        if (value instanceof Map<?, ?> nestedMap) {
            return (Map<String, Object>) nestedMap;
        }
        return Map.of();
    }

    private void mergeEditableOverrides(Map<String, Object> target,
            Map<String, Object> updatedArgs,
            Set<String> editableKeys) {
        for (String editableKey : editableKeys) {
            if (updatedArgs.containsKey(editableKey)) {
                target.put(editableKey, updatedArgs.get(editableKey));
            }
        }
    }
}
