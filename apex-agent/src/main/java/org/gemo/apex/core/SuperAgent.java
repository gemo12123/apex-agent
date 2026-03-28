package org.gemo.apex.core;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.component.interceptor.ToolInterceptor;
import org.gemo.apex.component.tool.GlobalToolRegistry;
import org.gemo.apex.constant.AskHumanInteractionType;
import org.gemo.apex.constant.ChangeType;
import org.gemo.apex.constant.Constant;
import org.gemo.apex.constant.ContextKeyEnum;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.constant.TaskStatus;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.exception.HumanInTheLoopException;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.gemo.apex.memory.recall.MemoryRecallService;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.memory.write.MemoryLifecycleManager;
import org.gemo.apex.message.AskHumanMessage;
import org.gemo.apex.message.TaskThinkChangeMessage;
import org.gemo.apex.service.AgentWorkspaceService;
import org.gemo.apex.tool.AskHumanTool;
import org.gemo.apex.util.JacksonUtils;
import org.gemo.apex.util.MessageUtils;
import org.apache.commons.lang3.StringUtils;
import org.gemo.apex.message.StreamContentMessage;
import org.gemo.apex.message.StreamThinkMessage;
import org.gemo.apex.tool.skills.CustomSkillsTool;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SuperAgent 核心执行引擎。
 */
@Slf4j
@Component
public class SuperAgent {

    private static final int MAX_ITERATIONS = 30;

    @Autowired
    private ToolInterceptor toolInterceptor;

    @Autowired
    private ToolCallingManager toolCallingManager;

    @Autowired
    private AgentWorkspaceService agentWorkspaceService;

    @Autowired
    private GlobalToolRegistry globalToolRegistry;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ConversationMemoryManager conversationMemoryManager;

    @Autowired
    private MemoryRecallService memoryRecallService;

    @Autowired
    private SessionContextStore sessionContextStore;

    @Autowired
    private MemoryLifecycleManager memoryLifecycleManager;

    /**
     * 执行完整的多阶段对话循环。
     */
    public void execute(SuperAgentContext context) {
        resumeFromSuspended(context);

        int loopCount = 0;
        try {
            while (loopCount < MAX_ITERATIONS) {
                loopCount++;

                SuperAgentContext.Stage currentLoopOrigStage = context.getCurrentStage();
                ToolPlan toolPlan = resolveToolsForCurrentStage(context);

                String stageSystemPrompt = buildStageSystemPrompt(context, toolPlan.promptDescribedTools());
//                context.setMemoryRecallPackage(memoryRecallService.recall(context));
                conversationMemoryManager.refreshFixedMessages(context, stageSystemPrompt);
                conversationMemoryManager.compactIfNeeded(context);

                com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions options =
                        com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions.builder()
                                .withInternalToolExecutionEnabled(false)
                                .withToolCallbacks(toolPlan.callableTools())
                                .withToolContext(Map.of(ToolContextKeys.SESSION_CONTEXT, context))
                                .build();
                Prompt promptToLlm = new Prompt(conversationMemoryManager.buildModelMessages(context), options);

                log.info(">>>>>> 核心引擎循环启动，第 {} 轮，当前阶段: {}", loopCount, context.getCurrentStage());
                ChatResponse response = callModel(promptToLlm, context);
                AssistantMessage assistantMessage = response.getResult().getOutput();

                if (currentLoopOrigStage != SuperAgentContext.Stage.MODE_CONFIRMATION) {
                    conversationMemoryManager.appendDialogueMessage(context, assistantMessage);
                }

                if (hasToolCalls(assistantMessage)) {
                    ToolResponseMessage interceptRes = toolInterceptor.interceptIllegalToolCalls(context,
                            assistantMessage.getToolCalls());
                    if (interceptRes != null) {
                        if (currentLoopOrigStage != SuperAgentContext.Stage.MODE_CONFIRMATION) {
                            conversationMemoryManager.appendDialogueMessage(context, interceptRes);
                        }
                        continue;
                    }
                    boolean directAnswered = handleToolCalls(promptToLlm, response, context);
                    if (directAnswered) {
                        break;
                    }
                } else {
                    if (context.getCurrentStage() == SuperAgentContext.Stage.THINKING) {
                        context.setCurrentStage(SuperAgentContext.Stage.MODE_CONFIRMATION);
                    } else if (context.getCurrentStage() == SuperAgentContext.Stage.MODE_CONFIRMATION) {
                        // MODE_CONFIRMATION 未产生工具调用时不再继续空转，直接结束本轮推理。
                        break;
                    } else if (context.getCurrentStage() == SuperAgentContext.Stage.EXECUTION) {
                        break;
                    }
                }
            }

            if (loopCount >= MAX_ITERATIONS) {
                log.error("SuperAgent 循环超过安全上限 {}，sessionId={}", MAX_ITERATIONS, context.getSessionId());
            }
            if (context.getExecutionStatus() == ExecutionStatus.IN_PROGRESS) {
                context.setExecutionStatus(ExecutionStatus.COMPLETED);
            }
        } catch (HumanInTheLoopException ex) {
            persistContext(context);
            memoryLifecycleManager.onTurnCompleted(context);
            throw ex;
        } finally {
            if (context.getExecutionStatus() != ExecutionStatus.HUMAN_IN_THE_LOOP) {
                persistContext(context);
                memoryLifecycleManager.onTurnCompleted(context);
            }
        }
    }

    /**
     * 恢复 ask_human 挂起后的上下文。
     */
    private void resumeFromSuspended(SuperAgentContext context) {
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
                            : "用户未输入";
                    toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), responseData));
                }
            }

            if (!toolResponses.isEmpty()) {
                List<ToolResponseMessage.ToolResponse> missingResponses = toolResponses.stream()
                        .filter(resp -> context.getMessages().isEmpty()
                                || !(context.getMessages().get(context.getMessages().size() - 1) instanceof ToolResponseMessage)
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

    /**
     * 处理工具调用。
     */
    private boolean handleToolCalls(Prompt input, ChatResponse response, SuperAgentContext context) {
        SuperAgentContext.Stage currentLoopOrigStage = context.getCurrentStage();

        List<AssistantMessage.ToolCall> allToolCalls = response.getResult().getOutput().getToolCalls();
        List<AssistantMessage.ToolCall> askHumanCalls = new ArrayList<>();
        List<AssistantMessage.ToolCall> directAnswerCalls = new ArrayList<>();
        List<AssistantMessage.ToolCall> otherToolCalls = new ArrayList<>();

        for (AssistantMessage.ToolCall toolCall : allToolCalls) {
            if (ToolNames.ASK_HUMAN.equals(toolCall.name())) {
                askHumanCalls.add(toolCall);
            } else if (ToolNames.DIRECT_ANSWER.equals(toolCall.name())) {
                directAnswerCalls.add(toolCall);
            } else {
                otherToolCalls.add(toolCall);
            }
        }

        if (!directAnswerCalls.isEmpty()) {
            AssistantMessage.ToolCall directAnswerCall = directAnswerCalls.getFirst();
            AssistantMessage directAnswerMsg = AssistantMessage.builder().toolCalls(List.of(directAnswerCall)).build();
            ChatResponse directAnswerResponse = new ChatResponse(List.of(new Generation(directAnswerMsg)));
            try {
                ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(input, directAnswerResponse);
                ToolResponseMessage responseMessage = null;
                for (Message message : toolExecutionResult.conversationHistory()) {
                    if (message instanceof ToolResponseMessage toolResponseMessage) {
                        responseMessage = toolResponseMessage;
                    }
                }
                if (responseMessage != null) {
                    conversationMemoryManager.appendDialogueMessage(context, responseMessage);
                    String answerContent = responseMessage.getResponses().stream()
                            .filter(resp -> directAnswerCall.id().equals(resp.id()))
                            .map(ToolResponseMessage.ToolResponse::responseData)
                            .findFirst()
                            .orElse("");
                    if (answerContent.startsWith("\"") && answerContent.endsWith("\"") && answerContent.length() >= 2) {
                        try {
                            answerContent = JSON.parseObject(answerContent, String.class);
                        } catch (Exception ignore) {
                            answerContent = answerContent.substring(1, answerContent.length() - 1);
                        }
                    }
                    if (!answerContent.isEmpty()) {
                        String interactionId = UUID.randomUUID().toString();
                        MessageUtils.sendMessage(context, StreamContentMessage.builder()
                                .context(buildContext(context, ContextKeyEnum.CONTENT_ID.getKey(), interactionId))
                                .messages(List.of(new StreamThinkMessage.ContentMessage(answerContent)))
                                .build());
                    }
                }
            } catch (Exception ex) {
                if (currentLoopOrigStage != SuperAgentContext.Stage.MODE_CONFIRMATION) {
                    conversationMemoryManager.appendDialogueMessage(context,
                            ToolResponseMessage.builder()
                                    .responses(List.of(new ToolResponseMessage.ToolResponse(directAnswerCall.id(),
                                            directAnswerCall.name(), "direct_answer 执行异常: " + ex.getMessage())))
                                    .build());
                }
            }
            return true;
        }

        if (!otherToolCalls.isEmpty()) {
            AssistantMessage otherToolsMessage = AssistantMessage.builder().toolCalls(otherToolCalls).build();
            ChatResponse otherToolsResponse = new ChatResponse(List.of(new Generation(otherToolsMessage)));
            try {
                ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(input, otherToolsResponse);
                ToolResponseMessage responseMessage = null;
                for (Message message : toolExecutionResult.conversationHistory()) {
                    if (message instanceof ToolResponseMessage toolResponseMessage) {
                        responseMessage = toolResponseMessage;
                    }
                }
                if (responseMessage != null && currentLoopOrigStage != SuperAgentContext.Stage.MODE_CONFIRMATION) {
                    conversationMemoryManager.appendDialogueMessage(context, responseMessage);
                }
            } catch (Exception ex) {
                if (currentLoopOrigStage != SuperAgentContext.Stage.MODE_CONFIRMATION) {
                    conversationMemoryManager.appendDialogueMessage(context,
                            ToolResponseMessage.builder()
                                    .responses(otherToolCalls.stream()
                                            .map(toolCall -> new ToolResponseMessage.ToolResponse(toolCall.id(),
                                                    toolCall.name(),
                                                    "工具调用异常，请检查参数。错误: " + ex.getMessage()))
                                            .toList())
                                    .build());
                }
                return false;
            }
        }

        if (!askHumanCalls.isEmpty()) {
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
                                    .context(buildContext(context))
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
                if (currentLoopOrigStage != SuperAgentContext.Stage.MODE_CONFIRMATION) {
                    conversationMemoryManager.appendDialogueMessage(context,
                            ToolResponseMessage.builder().responses(errorResponses).build());
                }
                return false;
            }

            if (hasQuestions) {
                context.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
                throw new HumanInTheLoopException("等待用户输入: " + String.join(", ", allQuestions));
            }
        }
        return false;
    }

    /**
     * 构建当前阶段系统提示词。
     */
    private String buildStageSystemPrompt(SuperAgentContext context,
            List<org.springframework.ai.tool.ToolCallback> promptDescribedTools) {
        String agentKey = context.getAgentKey();

        String skillsXml = "";
        CustomSkillsTool skillsTool = context.getCustomSkillsTool();
        if (skillsTool != null && skillsTool.getSkillsXml() != null) {
            skillsXml = skillsTool.getSkillsXml();
        }

        StringBuilder toolsDesc = new StringBuilder();
        if (!promptDescribedTools.isEmpty()) {
            for (org.springframework.ai.tool.ToolCallback tool : promptDescribedTools) {
                toolsDesc.append("- ").append(tool.getToolDefinition().name())
                        .append(": ").append(tool.getToolDefinition().description()).append("\n");
            }
        } else {
            toolsDesc.append("无\n");
        }

        String stagePromptTemplate = "";
        if (context.getCurrentStage() == SuperAgentContext.Stage.THINKING) {
            stagePromptTemplate = agentWorkspaceService.getThinkingPrompt(agentKey);
        } else if (context.getCurrentStage() == SuperAgentContext.Stage.MODE_CONFIRMATION) {
            stagePromptTemplate = agentWorkspaceService.getModeConfirmationPrompt(agentKey);
        } else if (context.getCurrentStage() == SuperAgentContext.Stage.EXECUTION) {
            if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR) {
                stagePromptTemplate = context.getPlan() == null
                        ? agentWorkspaceService.getPlanExecutorWritePlanPrompt(agentKey)
                        : agentWorkspaceService.getPlanExecutorRunPrompt(agentKey);
            } else {
                stagePromptTemplate = agentWorkspaceService.getReActPrompt(agentKey);
            }
        }

        if (stagePromptTemplate == null) {
            stagePromptTemplate = "";
        }

        String parsedPrompt = stagePromptTemplate
                .replace("{skills}", skillsXml)
                .replace("{available_tools_desc}", toolsDesc.toString())
                .replace("{date}", Constant.DATE_TIME_FORMATTER.format(LocalDateTime.now()));

        String agentRules = agentWorkspaceService.getAgentRules(agentKey);
        if (StringUtils.isNotEmpty(agentRules)) {
            parsedPrompt = parsedPrompt + "\n\n=== Global Execution Rules ===\n" + agentRules;
        }
        return parsedPrompt;
    }

    /**
     * 调用模型并按阶段推送流式内容。
     */
    private ChatResponse callModel(Prompt prompt, SuperAgentContext context) {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<ChatResponse> finalResponseRef = new java.util.concurrent.atomic.AtomicReference<>();
        StringBuilder fullContent = new StringBuilder();
        String interactionId = UUID.randomUUID().toString();

        chatModel.stream(prompt).subscribe(response -> {
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return;
            }
            AssistantMessage output = response.getResult().getOutput();
            if (hasToolCalls(output)) {
                finalResponseRef.set(response);
                return;
            }
            if (output.getText() == null || output.getText().isEmpty()) {
                return;
            }

            fullContent.append(output.getText());
            if (context.getCurrentStage() == SuperAgentContext.Stage.THINKING) {
                MessageUtils.sendMessage(context, StreamThinkMessage.builder()
                        .context(buildContext(context, ContextKeyEnum.CONTENT_ID.getKey(), interactionId))
                        .messages(List.of(new StreamThinkMessage.ContentMessage(output.getText())))
                        .build());
            } else if (context.getCurrentStage() == SuperAgentContext.Stage.EXECUTION) {
                if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR && context.getPlan() != null) {
                    boolean allCompleted = context.getPlan().getStages() != null
                            && context.getPlan().getStages().stream()
                            .allMatch(stage -> stage.getStatus() == TaskStatus.COMPLETED);
                    if (!allCompleted && context.getCurrentStageId() != null) {
                        MessageUtils.sendMessage(context, TaskThinkChangeMessage.builder()
                                .context(buildContext(context, ContextKeyEnum.CONTENT_ID.getKey(), interactionId))
                                .messages(List.of(TaskThinkChangeMessage.TaskThinkChangeDetail.builder()
                                        .taskId(context.getCurrentStageId())
                                        .changeType(ChangeType.CONTENT_APPEND.name())
                                        .content(output.getText())
                                        .build()))
                                .build());
                    } else {
                        MessageUtils.sendMessage(context, StreamContentMessage.builder()
                                .context(buildContext(context, ContextKeyEnum.CONTENT_ID.getKey(), interactionId))
                                .messages(List.of(new StreamThinkMessage.ContentMessage(output.getText())))
                                .build());
                    }
                } else if (context.getExecutionMode() == ModeEnum.REACT) {
                    MessageUtils.sendMessage(context, StreamContentMessage.builder()
                            .context(buildContext(context, ContextKeyEnum.CONTENT_ID.getKey(), interactionId))
                            .messages(List.of(new StreamThinkMessage.ContentMessage(output.getText())))
                            .build());
                }
            }
        }, error -> {
            log.error("流式调用大模型失败: {}", error.getMessage(), error);
            latch.countDown();
        }, () -> {
            if (finalResponseRef.get() == null && fullContent.length() > 0) {
                AssistantMessage finalMsg = new AssistantMessage(fullContent.toString());
                finalResponseRef.set(new ChatResponse(List.of(new Generation(finalMsg))));
            }
            latch.countDown();
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("等待大模型结果被中断", e);
        }
        return finalResponseRef.get();
    }

    private void persistContext(SuperAgentContext context) {
        persistDialogueMessages(context);
        context.setNextMessageSortNo(context.getTurnStartSortNo() + context.getPersistedDialogueMessageIndex() + 1L);
        context.setLastActiveTime(LocalDateTime.now());
        sessionContextStore.save(context);
    }

    private void persistDialogueMessages(SuperAgentContext context) {
        int persistedIndex = context.getPersistedDialogueMessageIndex() != null
                ? context.getPersistedDialogueMessageIndex()
                : 0;
        if (persistedIndex >= context.getDialogueMessages().size()) {
            return;
        }
        List<Message> messagesToPersist = new ArrayList<>(
                context.getDialogueMessages().subList(persistedIndex, context.getDialogueMessages().size()));
        long baseSortNo = (context.getTurnStartSortNo() != null ? context.getTurnStartSortNo() : 0L) + persistedIndex;
        sessionContextStore.appendDialogueMessages(context.getSessionId(), context.getTurnNo(), baseSortNo,
                messagesToPersist);
        context.setPersistedDialogueMessageIndex(context.getDialogueMessages().size());
    }

    private Map<String, Object> buildContext(SuperAgentContext context, String... extraEntries) {
        if (context.getCurrentStage() == SuperAgentContext.Stage.THINKING) {
            return Map.of();
        }
        Map<String, Object> map = new HashMap<>();
        map.put(ContextKeyEnum.MODE.getKey(),
                context.getExecutionMode() != null ? context.getExecutionMode().getMode() : "");
        if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR && context.getCurrentStageId() != null) {
            map.put(ContextKeyEnum.STAGE_ID.getKey(), context.getCurrentStageId());
        }
        for (int i = 0; i + 1 < extraEntries.length; i += 2) {
            map.put(extraEntries[i], extraEntries[i + 1]);
        }
        return Map.copyOf(map);
    }

    private boolean hasToolCalls(AssistantMessage msg) {
        return msg != null && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty();
    }

    private ToolPlan resolveToolsForCurrentStage(SuperAgentContext context) {
        List<org.springframework.ai.tool.ToolCallback> callableTools = new ArrayList<>();
        List<org.springframework.ai.tool.ToolCallback> promptDescribedTools = new ArrayList<>();
        java.util.Set<String> builtInToolNames = java.util.Set.of(
                ToolNames.ASK_HUMAN, ToolNames.DIRECT_ANSWER, ToolNames.WRITE_MODE,
                Constant.PLAN_TOOL_NAME, Constant.REPLAN_TOOL_NAME, Constant.SKILLS_TOOL_NAME);

        for (org.springframework.ai.tool.ToolCallback tool : context.getAvailableTools()) {
            String toolName = tool.getToolDefinition().name();
            boolean isMcpOrSubAgent = !builtInToolNames.contains(toolName);

            if (context.getCurrentStage() == SuperAgentContext.Stage.THINKING) {
                if (Constant.SKILLS_TOOL_NAME.equals(toolName) || ToolNames.DIRECT_ANSWER.equals(toolName)) {
                    callableTools.add(tool);
                } else if (ToolNames.ASK_HUMAN.equals(toolName) || isMcpOrSubAgent) {
                    promptDescribedTools.add(tool);
                }
            } else if (context.getCurrentStage() == SuperAgentContext.Stage.MODE_CONFIRMATION) {
                if (ToolNames.WRITE_MODE.equals(toolName)) {
                    callableTools.add(tool);
                } else if (ToolNames.ASK_HUMAN.equals(toolName) || Constant.SKILLS_TOOL_NAME.equals(toolName)
                        || isMcpOrSubAgent) {
                    promptDescribedTools.add(tool);
                }
            } else if (context.getCurrentStage() == SuperAgentContext.Stage.EXECUTION) {
                if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR && context.getPlan() == null) {
                    if (Constant.PLAN_TOOL_NAME.equals(toolName)) {
                        callableTools.add(tool);
                    } else if (ToolNames.ASK_HUMAN.equals(toolName) || Constant.SKILLS_TOOL_NAME.equals(toolName)
                            || Constant.REPLAN_TOOL_NAME.equals(toolName) || isMcpOrSubAgent) {
                        promptDescribedTools.add(tool);
                    }
                } else {
                    if (ToolNames.DIRECT_ANSWER.equals(toolName)
                            || ToolNames.WRITE_MODE.equals(toolName)
                            || Constant.PLAN_TOOL_NAME.equals(toolName)) {
                        continue;
                    }
                    if (Constant.REPLAN_TOOL_NAME.equals(toolName)) {
                        if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR) {
                            callableTools.add(tool);
                        }
                    } else {
                        callableTools.add(tool);
                    }
                }
            }
        }
        return new ToolPlan(callableTools, promptDescribedTools);
    }

    /**
     * 当前阶段可用工具集合。
     */
    private record ToolPlan(List<org.springframework.ai.tool.ToolCallback> callableTools,
            List<org.springframework.ai.tool.ToolCallback> promptDescribedTools) {
    }
}
