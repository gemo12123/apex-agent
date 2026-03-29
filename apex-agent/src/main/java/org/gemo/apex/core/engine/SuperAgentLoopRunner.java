package org.gemo.apex.core.engine;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.component.interceptor.ToolInterceptor;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SuperAgentLoopRunner {

    static final int MAX_ITERATIONS = 30;

    private final StageToolResolver stageToolResolver;
    private final AgentPromptAssembler agentPromptAssembler;
    private final ModelResponseStreamer modelResponseStreamer;
    private final ToolInterceptor toolInterceptor;
    private final ToolCallProcessor toolCallProcessor;
    private final ConversationMemoryManager conversationMemoryManager;

    public SuperAgentLoopRunner(StageToolResolver stageToolResolver,
            AgentPromptAssembler agentPromptAssembler,
            ModelResponseStreamer modelResponseStreamer,
            ToolInterceptor toolInterceptor,
            ToolCallProcessor toolCallProcessor,
            ConversationMemoryManager conversationMemoryManager) {
        this.stageToolResolver = stageToolResolver;
        this.agentPromptAssembler = agentPromptAssembler;
        this.modelResponseStreamer = modelResponseStreamer;
        this.toolInterceptor = toolInterceptor;
        this.toolCallProcessor = toolCallProcessor;
        this.conversationMemoryManager = conversationMemoryManager;
    }

    public void run(SuperAgentContext context) {
        int loopCount = 0;
        while (loopCount < MAX_ITERATIONS) {
            loopCount++;

            SuperAgentContext.Stage currentLoopOrigStage = context.getCurrentStage();
            StageToolPlan toolPlan = stageToolResolver.resolve(context);
            Prompt promptToLlm = agentPromptAssembler.assemble(context, toolPlan);

            log.info(">>>>>> 核心引擎循环启动，第 {} 轮，当前阶段: {}", loopCount, context.getCurrentStage());
            ChatResponse response = modelResponseStreamer.stream(promptToLlm, context);
            AssistantMessage assistantMessage = response.getResult().getOutput();

            if (currentLoopOrigStage != SuperAgentContext.Stage.MODE_CONFIRMATION) {
                conversationMemoryManager.appendDialogueMessage(context, assistantMessage);
            }

            if (EngineContextHelper.hasToolCalls(assistantMessage)) {
                ToolResponseMessage interceptRes = toolInterceptor.interceptIllegalToolCalls(context,
                        assistantMessage.getToolCalls());
                if (interceptRes != null) {
                    if (currentLoopOrigStage != SuperAgentContext.Stage.MODE_CONFIRMATION) {
                        conversationMemoryManager.appendDialogueMessage(context, interceptRes);
                    }
                    continue;
                }
                ToolCallProcessingResult result = toolCallProcessor.process(promptToLlm, assistantMessage, context,
                        currentLoopOrigStage);
                if (result.directAnswerTriggered()) {
                    break;
                }
                continue;
            }

            if (context.getCurrentStage() == SuperAgentContext.Stage.THINKING) {
                context.setCurrentStage(SuperAgentContext.Stage.MODE_CONFIRMATION);
                continue;
            }
            if (context.getCurrentStage() == SuperAgentContext.Stage.MODE_CONFIRMATION
                    || context.getCurrentStage() == SuperAgentContext.Stage.EXECUTION) {
                break;
            }
        }

        if (loopCount >= MAX_ITERATIONS) {
            log.error("SuperAgent 循环超过安全上限 {}，sessionId={}", MAX_ITERATIONS, context.getSessionId());
        }
        if (context.getExecutionStatus() == ExecutionStatus.IN_PROGRESS) {
            context.setExecutionStatus(ExecutionStatus.COMPLETED);
        }
    }
}
