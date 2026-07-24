package org.gemo.apex.core.engine;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.type.TypeReference;
import org.gemo.apex.constant.AskHumanInteractionType;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.domain.interaction.InteractionType;
import org.gemo.apex.domain.interaction.PendingHumanInteraction;
import org.gemo.apex.domain.interaction.PendingToolExecution;
import org.gemo.apex.exception.HumanInTheLoopException;
import org.gemo.apex.hook.lifecycle.AgentHookResult;
import org.gemo.apex.hook.lifecycle.AgentLifecycleHookRuntime;
import org.gemo.apex.hook.lifecycle.AgentRuntimeContext;
import org.gemo.apex.hook.lifecycle.HookDispatchResult;
import org.gemo.apex.hook.lifecycle.HookFlowAction;
import org.gemo.apex.hook.lifecycle.HookPoint;
import org.gemo.apex.hook.lifecycle.ToolCallRecord;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.gemo.apex.message.AskHumanMessage;
import org.gemo.apex.message.ToolConfirmationMessage;
import org.gemo.apex.tool.AskHumanTool;
import org.gemo.apex.util.JacksonUtils;
import org.gemo.apex.util.MessageUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class ToolCallProcessor {

    private final AgentToolExecutor agentToolExecutor;
    private final ConversationMemoryManager conversationMemoryManager;
    private final AgentLifecycleHookRuntime lifecycleHookRuntime;

    public ToolCallProcessor(AgentToolExecutor agentToolExecutor,
            ConversationMemoryManager conversationMemoryManager) {
        this(agentToolExecutor, conversationMemoryManager, null);
    }

    @Autowired
    public ToolCallProcessor(AgentToolExecutor agentToolExecutor,
            ConversationMemoryManager conversationMemoryManager,
            AgentLifecycleHookRuntime lifecycleHookRuntime) {
        this.agentToolExecutor = agentToolExecutor;
        this.conversationMemoryManager = conversationMemoryManager;
        this.lifecycleHookRuntime = lifecycleHookRuntime;
    }

    public ToolCallProcessingResult process(Prompt input, AssistantMessage assistantMessage,
            SuperAgentContext context, SuperAgentContext.Stage currentLoopOrigStage) {
        return process(input, assistantMessage, context, currentLoopOrigStage, null);
    }

    public ToolCallProcessingResult process(Prompt input, AssistantMessage assistantMessage,
            SuperAgentContext context, SuperAgentContext.Stage currentLoopOrigStage,
            AgentRuntimeContext runtimeContext) {
        if (runtimeContext != null && lifecycleHookRuntime != null) {
            return processWithLifecycleHooks(input, assistantMessage, context, runtimeContext);
        }
        List<AssistantMessage.ToolCall> askHumanCalls = new ArrayList<>();
        List<AssistantMessage.ToolCall> otherToolCalls = new ArrayList<>();

        for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
            if (ToolNames.ASK_HUMAN.equals(toolCall.name())) {
                askHumanCalls.add(toolCall);
            } else {
                otherToolCalls.add(toolCall);
            }
        }

        if (!otherToolCalls.isEmpty()) {
            processOtherTools(input, context, otherToolCalls);
        }

        if (!askHumanCalls.isEmpty()) {
            processAskHuman(context, askHumanCalls);
        }

        return ToolCallProcessingResult.continueLoop();
    }

    private ToolCallProcessingResult processWithLifecycleHooks(Prompt input, AssistantMessage assistantMessage,
            SuperAgentContext context, AgentRuntimeContext runtimeContext) {
        List<AssistantMessage.ToolCall> calls = assistantMessage.getToolCalls();
        Set<String> respondedCallIds = new java.util.HashSet<>();
        for (int index = 0; index < calls.size(); index++) {
            AssistantMessage.ToolCall toolCall = calls.get(index);
            runtimeContext.setCurrentToolCall(toolCall);
            runtimeContext.setCurrentToolArguments(parseArguments(toolCall.arguments()));
            runtimeContext.setCurrentToolOriginalResult(null);
            runtimeContext.setCurrentToolResult(null);

            HookDispatchResult preDispatch = lifecycleHookRuntime.run(
                    HookPoint.PRE_TOOL_CALL,
                    runtimeContext,
                    resolveSkippedHooks(context, toolCall));
            AgentHookResult preResult = preDispatch.getResult();
            HookFlowAction preAction = preResult.getAction();

            if (preAction == HookFlowAction.REQUEST_CONFIRMATION) {
                suspendForConfirmation(context, runtimeContext, toolCall, index, preDispatch);
            }
            if (preAction == HookFlowAction.BLOCK_TOOL) {
                String reason = preResult.getBlockReason() != null ? preResult.getBlockReason() : "tool blocked by hook";
                appendToolResponse(context, runtimeContext, toolCall, reason);
                respondedCallIds.add(toolCall.id());
                recordToolCall(runtimeContext, toolCall, false, preAction, reason, null);
                continue;
            }
            if (preAction == HookFlowAction.SKIP_TRACE || preAction == HookFlowAction.END_TURN) {
                markFlow(runtimeContext, preAction);
                appendMissingResponses(context, runtimeContext, calls, respondedCallIds, index,
                        preAction == HookFlowAction.SKIP_TRACE ? "tool skipped by lifecycle hook"
                                : "turn ended by lifecycle hook");
                return preAction == HookFlowAction.END_TURN
                        ? ToolCallProcessingResult.terminateLoop()
                        : ToolCallProcessingResult.continueLoop();
            }

            if (ToolNames.ASK_HUMAN.equals(toolCall.name())) {
                processAskHuman(context, List.of(toolCall));
                continue;
            }

            String invocationId = UUID.randomUUID().toString();
            AssistantMessage effectiveAssistant = AssistantMessage.builder()
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                            toolCall.id(),
                            toolCall.type(),
                            toolCall.name(),
                            JacksonUtils.toJson(runtimeContext.getCurrentToolArguments()))))
                    .build();
            try {
                ToolResponseMessage rawResponse = agentToolExecutor.execute(input, effectiveAssistant);
                String rawResult = rawResponse.getResponses().isEmpty()
                        ? ""
                        : rawResponse.getResponses().getFirst().responseData();
                runtimeContext.setCurrentToolOriginalResult(rawResult);
                runtimeContext.setCurrentToolResult(rawResult);

                HookDispatchResult postDispatch = lifecycleHookRuntime.run(HookPoint.POST_TOOL_CALL, runtimeContext);
                String finalResult = runtimeContext.getCurrentToolResult() != null
                        ? runtimeContext.getCurrentToolResult()
                        : "";
                appendToolResponse(context, runtimeContext, toolCall, finalResult);
                respondedCallIds.add(toolCall.id());
                activateSkillIfNeeded(runtimeContext, toolCall);
                recordToolCall(runtimeContext, toolCall, true, postDispatch.getResult().getAction(), null, invocationId);

                HookFlowAction postAction = postDispatch.getResult().getAction();
                if (postAction == HookFlowAction.SKIP_TRACE || postAction == HookFlowAction.END_TURN) {
                    markFlow(runtimeContext, postAction);
                    appendMissingResponses(context, runtimeContext, calls, respondedCallIds, index + 1,
                            postAction == HookFlowAction.SKIP_TRACE ? "tool skipped by lifecycle hook"
                                    : "turn ended by lifecycle hook");
                    return postAction == HookFlowAction.END_TURN
                            ? ToolCallProcessingResult.terminateLoop()
                            : ToolCallProcessingResult.continueLoop();
                }
            } catch (HumanInTheLoopException ex) {
                throw ex;
            } catch (Exception ex) {
                String error = "工具调用异常，请检查参数。错误: " + ex.getMessage();
                appendToolResponse(context, runtimeContext, toolCall, error);
                respondedCallIds.add(toolCall.id());
                recordToolCall(runtimeContext, toolCall, false, HookFlowAction.CONTINUE, error, invocationId);
            }
        }
        return ToolCallProcessingResult.continueLoop();
    }

    private LinkedHashMap<String, Object> parseArguments(String rawArguments) {
        Map<String, Object> parsed = JacksonUtils.fromJson(rawArguments, new TypeReference<Map<String, Object>>() {
        });
        return parsed != null ? new LinkedHashMap<>(parsed) : new LinkedHashMap<>();
    }

    private void appendToolResponse(SuperAgentContext context, AgentRuntimeContext runtimeContext,
            AssistantMessage.ToolCall toolCall, String result) {
        ToolResponseMessage response = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        toolCall.id(), toolCall.name(), result != null ? result : "")))
                .build();
        conversationMemoryManager.appendDialogueMessage(context, response);
        runtimeContext.getWorkingMessages().add(response);
    }

    private void appendMissingResponses(SuperAgentContext context, AgentRuntimeContext runtimeContext,
            List<AssistantMessage.ToolCall> calls, Set<String> respondedCallIds, int fromIndex, String reason) {
        for (int index = Math.max(0, fromIndex); index < calls.size(); index++) {
            AssistantMessage.ToolCall call = calls.get(index);
            if (respondedCallIds.add(call.id())) {
                appendToolResponse(context, runtimeContext, call, reason);
                recordToolCall(runtimeContext, call, false, runtimeContext.getTrace().getFlowAction(), reason, null);
            }
        }
    }

    private void recordToolCall(AgentRuntimeContext runtimeContext, AssistantMessage.ToolCall call,
            boolean succeeded, HookFlowAction action, String error, String invocationId) {
        ToolCallRecord record = ToolCallRecord.builder()
                .toolCallId(call.id())
                .invocationId(invocationId)
                .toolName(call.name())
                .arguments(new LinkedHashMap<>(runtimeContext.getCurrentToolArguments()))
                .originalResult(runtimeContext.getCurrentToolOriginalResult())
                .finalResult(runtimeContext.getCurrentToolResult())
                .succeeded(succeeded)
                .action(action)
                .error(error)
                .build();
        runtimeContext.getTrace().getToolCalls().add(record);
        runtimeContext.getTurnToolCalls().add(record);
    }

    private void markFlow(AgentRuntimeContext runtimeContext, HookFlowAction action) {
        runtimeContext.getTrace().setFlowAction(action);
        if (action == HookFlowAction.SKIP_TRACE) {
            runtimeContext.getTrace().setStatus(org.gemo.apex.hook.lifecycle.AgentTrace.Status.SKIPPED);
        }
    }

    private Set<String> resolveSkippedHooks(SuperAgentContext context, AssistantMessage.ToolCall toolCall) {
        PendingToolExecution pending = context.getPendingToolExecution();
        if (pending == null || !toolCall.id().equals(pending.getToolCallId())) {
            return Set.of();
        }
        return pending.getExecutedPreHookBeans() != null
                ? Set.copyOf(pending.getExecutedPreHookBeans())
                : Set.of();
    }

    private void suspendForConfirmation(SuperAgentContext context, AgentRuntimeContext runtimeContext,
            AssistantMessage.ToolCall toolCall, int toolIndex, HookDispatchResult dispatchResult) {
        var spec = dispatchResult.getResult().getConfirmationSpec();
        if (spec == null) {
            throw new IllegalStateException("REQUEST_CONFIRMATION 缺少 confirmationSpec");
        }
        String invocationId = UUID.randomUUID().toString();
        List<String> editableKeys = spec.getEditableFields() != null
                ? spec.getEditableFields().stream().map(field -> field.getKey()).toList()
                : List.of();
        context.setPendingHumanInteraction(PendingHumanInteraction.builder()
                .interactionType(InteractionType.TOOL_CONFIRMATION.name())
                .toolCallId(toolCall.id())
                .invocationId(invocationId)
                .confirmationId(spec.getConfirmationId())
                .build());
        context.setPendingToolExecution(PendingToolExecution.builder()
                .toolCallId(toolCall.id())
                .toolName(toolCall.name())
                .invocationId(invocationId)
                .resolvedArguments(new LinkedHashMap<>(runtimeContext.getCurrentToolArguments()))
                .editableFieldKeys(editableKeys)
                .confirmationId(spec.getConfirmationId())
                .executedPreHookBeans(dispatchResult.getExecutedHookBeans())
                .turnNo(context.getTurnNo())
                .traceNo(context.getTraceNo())
                .toolIndex(toolIndex)
                .build());
        context.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
        runtimeContext.getTrace().setStatus(org.gemo.apex.hook.lifecycle.AgentTrace.Status.SUSPENDED);
        runtimeContext.getTrace().setFlowAction(HookFlowAction.REQUEST_CONFIRMATION);
        MessageUtils.sendMessage(context, ToolConfirmationMessage.from(context, toolCall, invocationId, spec));
        throw new HumanInLoopExceptionAdapter();
    }

    private void activateSkillIfNeeded(AgentRuntimeContext runtimeContext, AssistantMessage.ToolCall toolCall) {
        if (!ToolNames.ACTIVATE_SKILL.equals(toolCall.name())) {
            return;
        }
        Object skillName = runtimeContext.getCurrentToolArguments().get("command");
        if (skillName == null) {
            return;
        }
        String value = String.valueOf(skillName);
        if (!runtimeContext.getActiveSkillNames().contains(value)) {
            runtimeContext.getActiveSkillNames().add(value);
        }
        runtimeContext.getSessionContext().setActiveSkillNames(
                new ArrayList<>(runtimeContext.getActiveSkillNames()));
    }

    private static final class HumanInLoopExceptionAdapter extends HumanInTheLoopException {
        private HumanInLoopExceptionAdapter() {
            super("等待工具确认");
        }
    }

    private void processOtherTools(Prompt input, SuperAgentContext context,
            List<AssistantMessage.ToolCall> otherToolCalls) {
        for (AssistantMessage.ToolCall toolCall : otherToolCalls) {
            try {
                ToolResponseMessage responseMessage = agentToolExecutor.execute(input,
                        AssistantMessage.builder().toolCalls(List.of(toolCall)).build());
                conversationMemoryManager.appendDialogueMessage(context, responseMessage);
            } catch (HumanInTheLoopException ex) {
                throw ex;
            } catch (Exception ex) {
                conversationMemoryManager.appendDialogueMessage(context,
                        ToolResponseMessage.builder()
                                .responses(List.of(new ToolResponseMessage.ToolResponse(toolCall.id(),
                                        toolCall.name(),
                                        "工具调用异常，请检查参数。错误: " + ex.getMessage())))
                                .build());
            }
        }
    }

    private void processAskHuman(SuperAgentContext context, List<AssistantMessage.ToolCall> askHumanCalls) {
        boolean hasQuestions = false;
        List<String> allQuestions = new ArrayList<>();
        List<ToolResponseMessage.ToolResponse> errorResponses = new ArrayList<>();

        for (AssistantMessage.ToolCall askHumanCall : askHumanCalls) {
            try {
                List<AskHumanTool.Request> requests = JacksonUtils.fromJson(
                        JSON.parseObject(askHumanCall.arguments()).getString("arg0"),
                        new TypeReference<>() {
                        });
                if (!CollectionUtils.isEmpty(requests)) {
                    List<AskHumanMessage.AskHumanDetail> detailsForThisCall = new ArrayList<>();
                    for (AskHumanTool.Request request : requests) {
                        detailsForThisCall.add(AskHumanMessage.AskHumanDetail.builder()
                                .inputType(request.interactionType() != null
                                        ? request.interactionType()
                                        : AskHumanInteractionType.TEXT_INPUT.name())
                                .question(request.question())
                                .options(request.options())
                                .toolCallId(askHumanCall.id())
                                .build());
                        allQuestions.add(request.question());
                    }
                    if (!detailsForThisCall.isEmpty()) {
                        hasQuestions = true;
                        MessageUtils.sendMessage(context, AskHumanMessage.builder()
                                .context(EngineContextHelper.buildMessageContext(context))
                                .messages(detailsForThisCall)
                                .build());
                    }
                }
            } catch (Exception ex) {
                errorResponses.add(new ToolResponseMessage.ToolResponse(askHumanCall.id(), askHumanCall.name(),
                        "参数解析失败"));
            }
        }

        if (!errorResponses.isEmpty()) {
            conversationMemoryManager.appendDialogueMessage(context,
                    ToolResponseMessage.builder().responses(errorResponses).build());
            return;
        }

        if (hasQuestions) {
            context.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
            throw new HumanInTheLoopException("等待用户输入: " + String.join(", ", allQuestions));
        }
    }
}
