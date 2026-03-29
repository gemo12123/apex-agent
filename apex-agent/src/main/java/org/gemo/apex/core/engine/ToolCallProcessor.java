package org.gemo.apex.core.engine;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.type.TypeReference;
import org.gemo.apex.constant.AskHumanInteractionType;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.exception.HumanInTheLoopException;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.gemo.apex.message.AskHumanMessage;
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

    private void processOtherTools(Prompt input, SuperAgentContext context,
            List<AssistantMessage.ToolCall> otherToolCalls) {
        try {
            ToolResponseMessage responseMessage = agentToolExecutor.execute(input,
                    AssistantMessage.builder().toolCalls(otherToolCalls).build());
            conversationMemoryManager.appendDialogueMessage(context, responseMessage);
        } catch (Exception ex) {
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
