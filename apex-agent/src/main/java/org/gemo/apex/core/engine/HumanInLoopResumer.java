package org.gemo.apex.core.engine;

import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.domain.interaction.InteractionType;
import org.gemo.apex.domain.interaction.PendingHumanInteraction;
import org.gemo.apex.domain.interaction.PendingToolExecution;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.gemo.apex.hook.lifecycle.AgentHookResult;
import org.gemo.apex.hook.lifecycle.AgentLifecycleHookRuntime;
import org.gemo.apex.hook.lifecycle.AgentRuntimeContext;
import org.gemo.apex.hook.lifecycle.HookDispatchResult;
import org.gemo.apex.hook.lifecycle.HookFlowAction;
import org.gemo.apex.hook.lifecycle.HookPoint;
import org.gemo.apex.hook.lifecycle.ToolCallRecord;
import org.gemo.apex.util.JacksonUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final AgentLifecycleHookRuntime lifecycleHookRuntime;

    public HumanInLoopResumer(ConversationMemoryManager conversationMemoryManager,
            AgentToolExecutor agentToolExecutor,
            AgentPromptAssembler agentPromptAssembler) {
        this(conversationMemoryManager, agentToolExecutor, agentPromptAssembler, null);
    }

    @Autowired
    public HumanInLoopResumer(ConversationMemoryManager conversationMemoryManager,
            AgentToolExecutor agentToolExecutor,
            AgentPromptAssembler agentPromptAssembler,
            AgentLifecycleHookRuntime lifecycleHookRuntime) {
        this.conversationMemoryManager = conversationMemoryManager;
        this.agentToolExecutor = agentToolExecutor;
        this.agentPromptAssembler = agentPromptAssembler;
        this.lifecycleHookRuntime = lifecycleHookRuntime;
    }

    public void resume(SuperAgentContext context) {
        resume(context, null);
    }

    public void resume(SuperAgentContext context, AgentRuntimeContext runtimeContext) {
        if (context.getExecutionStatus() != ExecutionStatus.HUMAN_IN_THE_LOOP) {
            return;
        }

        if (isToolConfirmation(context.getPendingHumanInteraction(), context.getPendingToolExecution())) {
            resumeToolConfirmation(context, runtimeContext);
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

    private void resumeToolConfirmation(SuperAgentContext context, AgentRuntimeContext runtimeContext) {
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

            AssistantMessage assistantMessage = AssistantMessage.builder()
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                            pendingExecution.getToolCallId(),
                            "function",
                            pendingExecution.getToolName(),
                            JacksonUtils.toJson(mergedArguments))))
                    .build();
            AssistantMessage.ToolCall resumedCall = assistantMessage.getToolCalls().getFirst();
            if (runtimeContext != null) {
                runtimeContext.setCurrentToolCall(resumedCall);
                runtimeContext.setCurrentToolArguments(new LinkedHashMap<>(mergedArguments));
                HookDispatchResult remainingPreHooks = lifecycleHookRuntime.run(
                        HookPoint.PRE_TOOL_CALL,
                        runtimeContext,
                        pendingExecution.getExecutedPreHookBeans() != null
                                ? Set.copyOf(pendingExecution.getExecutedPreHookBeans())
                                : Set.of());
                AgentHookResult preResult = remainingPreHooks.getResult();
                if (preResult.getAction() == HookFlowAction.BLOCK_TOOL
                        || preResult.getAction() == HookFlowAction.SKIP_TRACE
                        || preResult.getAction() == HookFlowAction.END_TURN) {
                    String reason = preResult.getBlockReason() != null
                            ? preResult.getBlockReason()
                            : "tool execution stopped by lifecycle hook";
                    ToolResponseMessage stoppedResponse = ToolResponseMessage.builder()
                            .responses(List.of(new ToolResponseMessage.ToolResponse(
                                    pendingExecution.getToolCallId(), pendingExecution.getToolName(), reason)))
                            .build();
                    conversationMemoryManager.appendDialogueMessage(context, stoppedResponse);
                    runtimeContext.getWorkingMessages().add(stoppedResponse);
                    runtimeContext.getTrace().setFlowAction(preResult.getAction());
                    clearPendingState(context);
                    return;
                }
                mergedArguments.clear();
                mergedArguments.putAll(runtimeContext.getCurrentToolArguments());
                assistantMessage = AssistantMessage.builder()
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                pendingExecution.getToolCallId(),
                                "function",
                                pendingExecution.getToolName(),
                                JacksonUtils.toJson(mergedArguments))))
                        .build();
            }

            Prompt prompt = agentPromptAssembler.assembleToolExecutionPrompt(context, Map.of(
                    ToolContextKeys.SKIP_PRE_HOOK_BEANS,
                    pendingExecution.getExecutedPreHookBeans() != null
                            ? List.copyOf(pendingExecution.getExecutedPreHookBeans())
                            : List.of()));
            ToolResponseMessage responseMessage = agentToolExecutor.execute(prompt, assistantMessage);
            if (runtimeContext != null && !responseMessage.getResponses().isEmpty()) {
                String rawResult = responseMessage.getResponses().getFirst().responseData();
                runtimeContext.setCurrentToolOriginalResult(rawResult);
                runtimeContext.setCurrentToolResult(rawResult);
                HookDispatchResult postDispatch = lifecycleHookRuntime.run(HookPoint.POST_TOOL_CALL, runtimeContext);
                String finalResult = runtimeContext.getCurrentToolResult();
                responseMessage = ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                pendingExecution.getToolCallId(),
                                pendingExecution.getToolName(),
                                finalResult != null ? finalResult : "")))
                        .build();
                ToolCallRecord record = ToolCallRecord.builder()
                        .toolCallId(pendingExecution.getToolCallId())
                        .invocationId(pendingExecution.getInvocationId())
                        .toolName(pendingExecution.getToolName())
                        .arguments(new LinkedHashMap<>(runtimeContext.getCurrentToolArguments()))
                        .originalResult(rawResult)
                        .finalResult(finalResult)
                        .succeeded(true)
                        .action(postDispatch.getResult().getAction())
                        .build();
                runtimeContext.getTrace().getToolCalls().add(record);
                runtimeContext.getTurnToolCalls().add(record);
                runtimeContext.getWorkingMessages().add(responseMessage);
                runtimeContext.getTrace().setFlowAction(postDispatch.getResult().getAction());
            }
            conversationMemoryManager.appendDialogueMessage(context, responseMessage);
        } else {
            ToolResponseMessage cancelledResponse = ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            pendingExecution.getToolCallId(),
                            pendingExecution.getToolName(),
                            "tool execution cancelled by user")))
                    .build();
            conversationMemoryManager.appendDialogueMessage(context, cancelledResponse);
            if (runtimeContext != null) {
                runtimeContext.getWorkingMessages().add(cancelledResponse);
                ToolCallRecord record = ToolCallRecord.builder()
                        .toolCallId(pendingExecution.getToolCallId())
                        .invocationId(pendingExecution.getInvocationId())
                        .toolName(pendingExecution.getToolName())
                        .arguments(pendingExecution.getResolvedArguments() != null
                                ? new LinkedHashMap<>(pendingExecution.getResolvedArguments())
                                : new LinkedHashMap<>())
                        .finalResult("tool execution cancelled by user")
                        .succeeded(false)
                        .action(HookFlowAction.BLOCK_TOOL)
                        .build();
                runtimeContext.getTrace().getToolCalls().add(record);
                runtimeContext.getTurnToolCalls().add(record);
            }
        }

        clearPendingState(context);
    }

    private void clearPendingState(SuperAgentContext context) {
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
