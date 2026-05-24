package org.gemo.apex.core;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.component.interceptor.ToolInterceptor;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.core.engine.AgentPromptAssembler;
import org.gemo.apex.core.engine.HumanInLoopResumer;
import org.gemo.apex.core.engine.ModelResponseStreamer;
import org.gemo.apex.core.engine.StageToolPlan;
import org.gemo.apex.core.engine.StageToolResolver;
import org.gemo.apex.core.engine.ToolCallProcessingResult;
import org.gemo.apex.core.engine.ToolCallProcessor;
import org.gemo.apex.exception.HumanInTheLoopException;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.memory.write.MemoryLifecycleManager;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SuperAgent {

    static final int MAX_ITERATIONS = 30;

    private final SuperAgentContext context;
    private final HumanInLoopResumer humanInLoopResumer;
    private final StageToolResolver stageToolResolver;
    private final AgentPromptAssembler agentPromptAssembler;
    private final ModelResponseStreamer modelResponseStreamer;
    private final ToolInterceptor toolInterceptor;
    private final ToolCallProcessor toolCallProcessor;
    private final ConversationMemoryManager conversationMemoryManager;
    private final SessionContextStore sessionContextStore;
    private final MemoryLifecycleManager memoryLifecycleManager;

    public SuperAgent(SuperAgentContext context,
            HumanInLoopResumer humanInLoopResumer,
            StageToolResolver stageToolResolver,
            AgentPromptAssembler agentPromptAssembler,
            ModelResponseStreamer modelResponseStreamer,
            ToolInterceptor toolInterceptor,
            ToolCallProcessor toolCallProcessor,
            ConversationMemoryManager conversationMemoryManager,
            SessionContextStore sessionContextStore,
            MemoryLifecycleManager memoryLifecycleManager) {
        this.context = context;
        this.humanInLoopResumer = humanInLoopResumer;
        this.stageToolResolver = stageToolResolver;
        this.agentPromptAssembler = agentPromptAssembler;
        this.modelResponseStreamer = modelResponseStreamer;
        this.toolInterceptor = toolInterceptor;
        this.toolCallProcessor = toolCallProcessor;
        this.conversationMemoryManager = conversationMemoryManager;
        this.sessionContextStore = sessionContextStore;
        this.memoryLifecycleManager = memoryLifecycleManager;
    }

    public SuperAgentContext getContext() {
        return context;
    }

    public void run() {
        humanInLoopResumer.resume(context);

        try {
            executeLoop();
        } catch (HumanInTheLoopException ex) {
            log.info("会话挂起等待用户回复, sessionId={}", context.getSessionId());
        } catch (RuntimeException ex) {
            if (context.getExecutionStatus() == ExecutionStatus.IN_PROGRESS) {
                context.setExecutionStatus(ExecutionStatus.FAILED);
            }
            log.error("SuperAgent execution failed, sessionId={}", context.getSessionId(), ex);
            throw ex;
        } finally {
            finalizeTurn();
        }
    }

    public void execute(SuperAgentContext context) {
        if (this.context != context) {
            throw new IllegalStateException("This SuperAgent instance is bound to another context");
        }
        run();
    }

    private void executeLoop() {
        int loopCount = 0;
        while (loopCount < MAX_ITERATIONS) {
            loopCount++;

            StageToolPlan toolPlan = stageToolResolver.resolve(context);
            Prompt promptToLlm = agentPromptAssembler.assemble(context, toolPlan);

            log.info(">>>>>> 核心引擎循环启动，第 {} 轮，当前阶段: {}", loopCount, context.getCurrentStage());
            ChatResponse response = modelResponseStreamer.stream(promptToLlm, context);
            AssistantMessage assistantMessage = response.getResult().getOutput();

            conversationMemoryManager.appendDialogueMessage(context, assistantMessage);

            if (hasToolCalls(assistantMessage)) {
                ToolResponseMessage interceptRes = toolInterceptor.interceptIllegalToolCalls(context,
                        assistantMessage.getToolCalls());
                if (interceptRes != null) {
                    conversationMemoryManager.appendDialogueMessage(context, interceptRes);
                    continue;
                }
                ToolCallProcessingResult result = toolCallProcessor.process(promptToLlm, assistantMessage, context,
                        context.getCurrentStage());
                if (result.directAnswerTriggered()) {
                    break;
                }
                continue;
            }

            break;
        }

        if (loopCount >= MAX_ITERATIONS) {
            log.error("SuperAgent 循环超过安全上限 {}，sessionId={}", MAX_ITERATIONS, context.getSessionId());
        }
        if (context.getExecutionStatus() == ExecutionStatus.IN_PROGRESS) {
            context.setExecutionStatus(ExecutionStatus.COMPLETED);
        }
    }

    private boolean hasToolCalls(AssistantMessage message) {
        return message != null && message.getToolCalls() != null && !message.getToolCalls().isEmpty();
    }

    private void finalizeTurn() {
        persistDialogueMessages();
        context.setNextMessageSortNo(context.getTurnStartSortNo() + context.getPersistedDialogueMessageIndex() + 1L);
        context.setLastActiveTime(LocalDateTime.now());
        sessionContextStore.save(context);
        memoryLifecycleManager.onTurnCompleted(context);
    }

    private void persistDialogueMessages() {
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
}
