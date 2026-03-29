package org.gemo.apex.core.engine;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.type.TypeReference;
import org.gemo.apex.constant.AskHumanInteractionType;
import org.gemo.apex.constant.ContextKeyEnum;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.exception.HumanInTheLoopException;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.gemo.apex.message.AskHumanMessage;
import org.gemo.apex.message.StreamContentMessage;
import org.gemo.apex.message.StreamThinkMessage;
import org.gemo.apex.tool.AskHumanTool;
import org.gemo.apex.util.JacksonUtils;
import org.gemo.apex.util.MessageUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ToolCallProcessor {

    private final AgentToolExecutor agentToolExecutor;
    private final ConversationMemoryManager conversationMemoryManager;

    public ToolCallProcessor(AgentToolExecutor agentToolExecutor,
            ConversationMemoryManager conversationMemoryManager) {
        this.agentToolExecutor = agentToolExecutor;
        this.conversationMemoryManager = conversationMemoryManager;
    }

    public ToolCallProcessingResult process(Prompt input, AssistantMessage assistantMessage,
            SuperAgentContext context, SuperAgentContext.Stage currentLoopOrigStage) {
        List<AssistantMessage.ToolCall> allToolCalls = assistantMessage.getToolCalls();
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
            return processDirectAnswer(input, context, currentLoopOrigStage, directAnswerCalls.getFirst());
        }

        if (!otherToolCalls.isEmpty()) {
            processOtherTools(input, context, currentLoopOrigStage, otherToolCalls);
        }

        if (!askHumanCalls.isEmpty()) {
            processAskHuman(context, currentLoopOrigStage, askHumanCalls);
        }

        return ToolCallProcessingResult.continueLoop();
    }

    private ToolCallProcessingResult processDirectAnswer(Prompt input, SuperAgentContext context,
            SuperAgentContext.Stage currentLoopOrigStage, AssistantMessage.ToolCall directAnswerCall) {
        try {
            ToolResponseMessage responseMessage = agentToolExecutor.execute(input,
                    AssistantMessage.builder().toolCalls(List.of(directAnswerCall)).build());
            conversationMemoryManager.appendDialogueMessage(context, responseMessage);

            String answerContent = responseMessage.getResponses().stream()
                    .filter(resp -> directAnswerCall.id().equals(resp.id()))
                    .map(ToolResponseMessage.ToolResponse::responseData)
                    .findFirst()
                    .map(this::normalizeDirectAnswerContent)
                    .orElse("");
            if (!answerContent.isEmpty()) {
                String interactionId = UUID.randomUUID().toString();
                MessageUtils.sendMessage(context, StreamContentMessage.builder()
                        .context(EngineContextHelper.buildMessageContext(context,
                                ContextKeyEnum.CONTENT_ID.getKey(), interactionId))
                        .messages(List.of(new StreamThinkMessage.ContentMessage(answerContent)))
                        .build());
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
        return ToolCallProcessingResult.terminateLoop();
    }

    private void processOtherTools(Prompt input, SuperAgentContext context,
            SuperAgentContext.Stage currentLoopOrigStage, List<AssistantMessage.ToolCall> otherToolCalls) {
        try {
            ToolResponseMessage responseMessage = agentToolExecutor.execute(input,
                    AssistantMessage.builder().toolCalls(otherToolCalls).build());
            if (currentLoopOrigStage != SuperAgentContext.Stage.MODE_CONFIRMATION) {
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
        }
    }

    private void processAskHuman(SuperAgentContext context, SuperAgentContext.Stage currentLoopOrigStage,
            List<AssistantMessage.ToolCall> askHumanCalls) {
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
            if (currentLoopOrigStage != SuperAgentContext.Stage.MODE_CONFIRMATION) {
                conversationMemoryManager.appendDialogueMessage(context,
                        ToolResponseMessage.builder().responses(errorResponses).build());
            }
            return;
        }

        if (hasQuestions) {
            context.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
            throw new HumanInTheLoopException("等待用户输入: " + String.join(", ", allQuestions));
        }
    }

    private String normalizeDirectAnswerContent(String answerContent) {
        if (answerContent.startsWith("\"") && answerContent.endsWith("\"") && answerContent.length() >= 2) {
            try {
                return JSON.parseObject(answerContent, String.class);
            } catch (Exception ignore) {
                return answerContent.substring(1, answerContent.length() - 1);
            }
        }
        return answerContent;
    }
}
