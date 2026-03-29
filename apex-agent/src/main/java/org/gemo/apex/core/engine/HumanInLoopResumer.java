package org.gemo.apex.core.engine;

import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HumanInLoopResumer {

    private final ConversationMemoryManager conversationMemoryManager;

    public HumanInLoopResumer(ConversationMemoryManager conversationMemoryManager) {
        this.conversationMemoryManager = conversationMemoryManager;
    }

    public void resume(SuperAgentContext context) {
        if (context.getExecutionStatus() != ExecutionStatus.HUMAN_IN_THE_LOOP
                || CollectionUtils.isEmpty(context.getMessages())) {
            return;
        }

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
}
