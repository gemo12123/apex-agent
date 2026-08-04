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
import org.gemo.apex.definition.agent.IAgentDefinitionLoader;
import org.gemo.apex.exception.HumanInTheLoopException;
import org.gemo.apex.hook.lifecycle.AgentExecutionStore;
import org.gemo.apex.hook.lifecycle.AgentLifecycleHookRuntime;
import org.gemo.apex.hook.lifecycle.AgentRuntimeContext;
import org.gemo.apex.hook.lifecycle.AgentIteration;
import org.gemo.apex.hook.lifecycle.AgentTurn;
import org.gemo.apex.hook.lifecycle.HookFlowAction;
import org.gemo.apex.hook.lifecycle.HookPoint;
import org.gemo.apex.hook.lifecycle.HookDispatchResult;
import org.gemo.apex.hook.lifecycle.InMemoryAgentExecutionStore;
import org.gemo.apex.hook.lifecycle.MessageMutationRecord;
import org.gemo.apex.hook.lifecycle.ToolCallRecord;
import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.definition.agent.AgentDefinition;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.gemo.apex.memory.session.SessionContextStore;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final IAgentDefinitionLoader agentDefinitionLoader;
    private final AgentLifecycleHookRuntime lifecycleHookRuntime;
    private final AgentExecutionStore agentExecutionStore;

    private AgentRuntimeContext runtimeContext;

    public SuperAgent(SuperAgentContext context,
            HumanInLoopResumer humanInLoopResumer,
            StageToolResolver stageToolResolver,
            AgentPromptAssembler agentPromptAssembler,
            ModelResponseStreamer modelResponseStreamer,
            ToolInterceptor toolInterceptor,
            ToolCallProcessor toolCallProcessor,
            ConversationMemoryManager conversationMemoryManager,
            SessionContextStore sessionContextStore) {
        this(context,
                humanInLoopResumer,
                stageToolResolver,
                agentPromptAssembler,
                modelResponseStreamer,
                toolInterceptor,
                toolCallProcessor,
                conversationMemoryManager,
                sessionContextStore,
                agentKey -> new AgentDefinition(agentKey, ModeEnum.REACT, List.of(), List.of(), List.of(),
                        AgentHooksConfig.empty(), "", "", "", ""),
                (point, runtime, skipped) -> HookDispatchResult.continued(),
                new InMemoryAgentExecutionStore());
    }

    public SuperAgent(SuperAgentContext context,
            HumanInLoopResumer humanInLoopResumer,
            StageToolResolver stageToolResolver,
            AgentPromptAssembler agentPromptAssembler,
            ModelResponseStreamer modelResponseStreamer,
            ToolInterceptor toolInterceptor,
            ToolCallProcessor toolCallProcessor,
            ConversationMemoryManager conversationMemoryManager,
            SessionContextStore sessionContextStore,
            IAgentDefinitionLoader agentDefinitionLoader,
            AgentLifecycleHookRuntime lifecycleHookRuntime,
            AgentExecutionStore agentExecutionStore) {
        this.context = context;
        this.humanInLoopResumer = humanInLoopResumer;
        this.stageToolResolver = stageToolResolver;
        this.agentPromptAssembler = agentPromptAssembler;
        this.modelResponseStreamer = modelResponseStreamer;
        this.toolInterceptor = toolInterceptor;
        this.toolCallProcessor = toolCallProcessor;
        this.conversationMemoryManager = conversationMemoryManager;
        this.sessionContextStore = sessionContextStore;
        this.agentDefinitionLoader = agentDefinitionLoader;
        this.lifecycleHookRuntime = lifecycleHookRuntime;
        this.agentExecutionStore = agentExecutionStore;
    }

    public SuperAgentContext getContext() {
        return context;
    }

    public void run() {
        boolean suspended = false;
        try {
            initializeRuntimeContext();
            boolean resumingSuspendedIteration = context.getExecutionStatus() == ExecutionStatus.HUMAN_IN_THE_LOOP
                    && context.getIterationNo() != null
                    && context.getIterationNo() > 0;
            StageToolPlan initialToolPlan = stageToolResolver.resolve(context);
            if (!resumingSuspendedIteration) {
                ensureWorkingMessages(initialToolPlan);
                runtimeContext.setEnabledTools(new ArrayList<>(initialToolPlan.callableTools()));
            } else {
                runtimeContext.setEnabledTools(resolveRestoredEnabledTools(initialToolPlan));
            }
            humanInLoopResumer.resume(context, runtimeContext);
            if (resumingSuspendedIteration && runtimeContext.getIteration() != null) {
                synchronizeResumedMessages();
                runtimeContext.getIteration().setStatus(AgentIteration.Status.IN_PROGRESS);
                if (runtimeContext.getIteration().getFlowAction() != HookFlowAction.SKIP_ITERATION
                        && runtimeContext.getIteration().getFlowAction() != HookFlowAction.END_TURN) {
                    resumeOutstandingToolCalls();
                }
                finishIteration(runtimeContext.getIteration());
                if (runtimeContext.getIteration().getFlowAction() == HookFlowAction.END_TURN) {
                    context.setExecutionStatus(ExecutionStatus.COMPLETED);
                    runtimeContext.getTurn().setStatus(AgentTurn.Status.ENDED_BY_HOOK);
                    completeTurn(AgentTurn.Status.ENDED_BY_HOOK);
                    return;
                }
            }
            if (context.getIterationNo() == null || context.getIterationNo() == 0) {
                lifecycleHookRuntime.run(HookPoint.TURN_START, runtimeContext);
                agentExecutionStore.saveTurn(runtimeContext.getTurn());
            }
            executeLoop();
        } catch (HumanInTheLoopException ex) {
            suspended = true;
            runtimeContext.getTurn().setStatus(AgentTurn.Status.SUSPENDED);
            agentExecutionStore.saveTurn(runtimeContext.getTurn());
            if (runtimeContext.getIteration() != null) {
                runtimeContext.getIteration().setStatus(AgentIteration.Status.SUSPENDED);
                agentExecutionStore.saveIteration(runtimeContext.getIteration());
            }
            log.info("会话挂起等待用户回复，sessionId={}, turnNo={}, iterationNo={}",
                    context.getSessionId(), context.getTurnNo(), context.getIterationNo());
        } catch (RuntimeException ex) {
            if (context.getExecutionStatus() == ExecutionStatus.IN_PROGRESS) {
                context.setExecutionStatus(ExecutionStatus.FAILED);
            }
            completeTurn(AgentTurn.Status.FAILED);
            log.error("SuperAgent 执行失败，sessionId={}", context.getSessionId(), ex);
            throw ex;
        } finally {
            finalizeInvocation(!suspended);
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
            refreshWorkingMessages(toolPlan);
            runtimeContext.setEnabledTools(new ArrayList<>(toolPlan.callableTools()));
            AgentIteration iteration = beginIteration();
            boolean iterationSuspended = false;

            try {
                lifecycleHookRuntime.run(HookPoint.ITERATION_START, runtimeContext);
                lifecycleHookRuntime.run(HookPoint.PRE_MODEL_CALL, runtimeContext);

                Prompt promptToLlm = agentPromptAssembler.assemble(
                        context,
                        toolPlan,
                        runtimeContext.getWorkingMessages(),
                        runtimeContext.getEnabledTools());
                if (promptToLlm == null) {
                    // 兼容只实现旧 assemble 重载的扩展和测试替身。
                    promptToLlm = agentPromptAssembler.assemble(context, toolPlan);
                }
                iteration.setModelInput(new ArrayList<>(promptToLlm.getInstructions()));
                agentExecutionStore.saveIteration(iteration);

                log.info("核心引擎 Iteration 启动，turnNo={}, iterationNo={}, 当前阶段={}",
                        context.getTurnNo(), context.getIterationNo(), context.getCurrentStage());
                ChatResponse response = modelResponseStreamer.stream(promptToLlm, context);
                AssistantMessage rawAssistantMessage = response.getResult().getOutput();
                iteration.setOriginalModelOutput(response);
                runtimeContext.setOriginalModelOutput(response);
                runtimeContext.getWorkingMessages().add(rawAssistantMessage);
                runtimeContext.setFinalModelOutput(rawAssistantMessage);

                lifecycleHookRuntime.run(HookPoint.POST_MODEL_CALL, runtimeContext);
                AssistantMessage assistantMessage = resolveFinalAssistantMessage(rawAssistantMessage);
                runtimeContext.setFinalModelOutput(assistantMessage);
                iteration.setFinalModelOutput(assistantMessage);
                if (assistantMessage != null) {
                    conversationMemoryManager.appendDialogueMessage(context, assistantMessage);
                }

                if (hasToolCalls(assistantMessage)) {
                    ToolResponseMessage interceptResponse = toolInterceptor.interceptIllegalToolCalls(context,
                            assistantMessage.getToolCalls());
                    if (interceptResponse != null) {
                        conversationMemoryManager.appendDialogueMessage(context, interceptResponse);
                        runtimeContext.getWorkingMessages().add(interceptResponse);
                        continue;
                    }
                    ToolCallProcessingResult result = toolCallProcessor.process(
                            promptToLlm,
                            assistantMessage,
                            context,
                            context.getCurrentStage(),
                            runtimeContext);
                    if (result.directAnswerTriggered()) {
                        iteration.setFlowAction(HookFlowAction.END_TURN);
                        runtimeContext.getTurn().setStatus(AgentTurn.Status.ENDED_BY_HOOK);
                        break;
                    }
                    continue;
                }
                break;
            } catch (HumanInTheLoopException ex) {
                iterationSuspended = true;
                iteration.setStatus(AgentIteration.Status.SUSPENDED);
                agentExecutionStore.saveIteration(iteration);
                throw ex;
            } catch (RuntimeException ex) {
                iteration.setStatus(AgentIteration.Status.FAILED);
                iteration.setError(ex.getMessage());
                throw ex;
            } finally {
                if (!iterationSuspended) {
                    finishIteration(iteration);
                }
            }
        }

        if (loopCount >= MAX_ITERATIONS) {
            log.error("SuperAgent 循环超过安全上限 {}，sessionId={}", MAX_ITERATIONS, context.getSessionId());
        }
        if (context.getExecutionStatus() == ExecutionStatus.IN_PROGRESS) {
            context.setExecutionStatus(ExecutionStatus.COMPLETED);
        }
        AgentTurn.Status finalStatus = runtimeContext.getTurn().getStatus() == AgentTurn.Status.ENDED_BY_HOOK
                ? AgentTurn.Status.ENDED_BY_HOOK
                : AgentTurn.Status.COMPLETED;
        completeTurn(finalStatus);
    }

    private boolean hasToolCalls(AssistantMessage message) {
        return message != null && message.getToolCalls() != null && !message.getToolCalls().isEmpty();
    }

    private void initializeRuntimeContext() {
        AgentTurn turn = agentExecutionStore.findTurn(context.getTurnNo()).orElseGet(() -> AgentTurn.builder()
                .turnNo(context.getTurnNo())
                .sessionId(context.getSessionId())
                .agentKey(context.getAgentKey())
                .userId(context.getUserId())
                .startedAt(LocalDateTime.now())
                .build());
        List<ToolCallRecord> persistedTurnToolCalls = agentExecutionStore.findIterations(context.getTurnNo()).stream()
                .flatMap(iteration -> iteration.getToolCalls().stream())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        runtimeContext = AgentRuntimeContext.builder()
                .executionStore(agentExecutionStore)
                .sessionContext(context)
                .agentDefinition(agentDefinitionLoader.load(context.getAgentKey()))
                .turn(turn)
                .workingMessages(context.getWorkingMessages() != null
                        ? new ArrayList<>(context.getWorkingMessages())
                        : new ArrayList<>())
                .workingMessagesInitialized((context.getWorkingMessages() != null
                        && !context.getWorkingMessages().isEmpty())
                        || (context.getIterationNo() != null && context.getIterationNo() > 0))
                .fixedMessageCount(context.getFixedMessages() != null ? context.getFixedMessages().size() : 0)
                .availableTools(new ArrayList<>(context.getAvailableTools()))
                .activeSkillNames(context.getActiveSkillNames() != null
                        ? new ArrayList<>(context.getActiveSkillNames())
                        : new ArrayList<>())
                .turnToolCalls(persistedTurnToolCalls)
                .build();
        if (context.getIterationNo() != null && context.getIterationNo() > 0) {
            AgentIteration suspendedIteration = agentExecutionStore.findIteration(context.getTurnNo(), context.getIterationNo())
                    .orElseGet(() -> AgentIteration.builder()
                            .turnNo(context.getTurnNo())
                            .iterationNo(context.getIterationNo())
                            .status(AgentIteration.Status.SUSPENDED)
                            .startedAt(LocalDateTime.now())
                            .build());
            runtimeContext.setIteration(suspendedIteration);
        }
    }

    private void synchronizeResumedMessages() {
        if (context.getDialogueMessages().isEmpty()) {
            return;
        }
        Message lastDialogueMessage = context.getDialogueMessages().getLast();
        List<Message> workingMessages = runtimeContext.getWorkingMessages();
        if (workingMessages.isEmpty() || workingMessages.getLast() != lastDialogueMessage) {
            workingMessages.add(lastDialogueMessage);
        }
    }

    private void resumeOutstandingToolCalls() {
        List<Message> messages = runtimeContext.getWorkingMessages();
        int assistantIndex = -1;
        AssistantMessage toolCallingMessage = null;
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index) instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
                assistantIndex = index;
                toolCallingMessage = assistantMessage;
                break;
            }
        }
        if (toolCallingMessage == null) {
            return;
        }

        Set<String> respondedCallIds = new HashSet<>();
        for (int index = assistantIndex + 1; index < messages.size(); index++) {
            if (messages.get(index) instanceof ToolResponseMessage responseMessage) {
                responseMessage.getResponses().forEach(response -> respondedCallIds.add(response.id()));
            }
        }
        List<AssistantMessage.ToolCall> outstandingCalls = toolCallingMessage.getToolCalls().stream()
                .filter(call -> !respondedCallIds.contains(call.id()))
                .toList();
        if (outstandingCalls.isEmpty()) {
            return;
        }

        Prompt toolPrompt = agentPromptAssembler.assembleToolExecutionPrompt(context, Map.of());
        toolCallProcessor.process(
                toolPrompt,
                AssistantMessage.builder().toolCalls(outstandingCalls).build(),
                context,
                context.getCurrentStage(),
                runtimeContext);
    }

    private void ensureWorkingMessages(StageToolPlan toolPlan) {
        if (!runtimeContext.isWorkingMessagesInitialized()) {
            List<Message> prepared = agentPromptAssembler.prepareWorkingMessages(context, toolPlan);
            runtimeContext.setWorkingMessages(prepared != null ? prepared : new ArrayList<>());
            runtimeContext.setWorkingMessagesInitialized(true);
            runtimeContext.setFixedMessageCount(context.getFixedMessages() != null
                    ? context.getFixedMessages().size()
                    : 0);
        }
        context.setWorkingMessages(runtimeContext.getWorkingMessages());
    }

    private void refreshWorkingMessages(StageToolPlan toolPlan) {
        ensureWorkingMessages(toolPlan);
        int fixedCount = Math.min(runtimeContext.getFixedMessageCount(), runtimeContext.getWorkingMessages().size());
        List<Message> retainedMessages = new ArrayList<>(
                runtimeContext.getWorkingMessages().subList(fixedCount, runtimeContext.getWorkingMessages().size()));
        agentPromptAssembler.refreshFixedMessages(context, toolPlan);
        List<Message> refreshedMessages = new ArrayList<>(context.getFixedMessages());
        refreshedMessages.addAll(retainedMessages);
        runtimeContext.setWorkingMessages(refreshedMessages);
        runtimeContext.setFixedMessageCount(context.getFixedMessages().size());
        context.setWorkingMessages(refreshedMessages);
    }

    private List<org.springframework.ai.tool.ToolCallback> resolveRestoredEnabledTools(StageToolPlan toolPlan) {
        if (context.getEnabledToolNames() == null || context.getEnabledToolNames().isEmpty()) {
            return new ArrayList<>(toolPlan.callableTools());
        }
        Set<String> enabledNames = Set.copyOf(context.getEnabledToolNames());
        return toolPlan.callableTools().stream()
                .filter(tool -> enabledNames.contains(tool.getToolDefinition().name()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private AgentIteration beginIteration() {
        int iterationNo = context.getIterationNo() != null ? context.getIterationNo() + 1 : 1;
        context.setIterationNo(iterationNo);
        AgentIteration iteration = AgentIteration.builder()
                .turnNo(context.getTurnNo())
                .iterationNo(iterationNo)
                .startedAt(LocalDateTime.now())
                .build();
        runtimeContext.setIteration(iteration);
        runtimeContext.getTurn().setLastIterationNo(iterationNo);
        agentExecutionStore.saveIteration(iteration);
        agentExecutionStore.saveTurn(runtimeContext.getTurn());
        return iteration;
    }

    private AssistantMessage resolveFinalAssistantMessage(AssistantMessage fallback) {
        List<Message> messages = runtimeContext.getWorkingMessages();
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            if (message == fallback) {
                return fallback;
            }
            for (MessageMutationRecord mutation : runtimeContext.getIteration().getMessageMutations()) {
                if (mutation.isApplied()
                        && mutation.getHookPoint() == HookPoint.POST_MODEL_CALL
                        && mutation.getAfterMessage() == message
                        && message instanceof AssistantMessage assistantMessage) {
                    return assistantMessage;
                }
            }
        }
        return null;
    }

    private void finishIteration(AgentIteration iteration) {
        if (iteration.getStatus() == AgentIteration.Status.IN_PROGRESS) {
            iteration.setStatus(iteration.getFlowAction() == HookFlowAction.SKIP_ITERATION
                    ? AgentIteration.Status.SKIPPED
                    : AgentIteration.Status.COMPLETED);
        }
        lifecycleHookRuntime.run(HookPoint.ITERATION_END, runtimeContext);
        iteration.setEndedAt(LocalDateTime.now());
        agentExecutionStore.saveIteration(iteration);
    }

    private void completeTurn(AgentTurn.Status status) {
        if (runtimeContext == null || runtimeContext.getTurn().getEndedAt() != null) {
            return;
        }
        runtimeContext.getTurn().setStatus(status);
        lifecycleHookRuntime.run(HookPoint.TURN_END, runtimeContext);
        runtimeContext.getTurn().setEndedAt(LocalDateTime.now());
        agentExecutionStore.saveTurn(runtimeContext.getTurn());
    }

    private void finalizeInvocation(boolean turnCompleted) {
        persistDialogueMessages();
        context.setWorkingMessages(runtimeContext != null
                ? new ArrayList<>(runtimeContext.getWorkingMessages())
                : context.getWorkingMessages());
        context.setActiveSkillNames(runtimeContext != null
                ? new ArrayList<>(runtimeContext.getActiveSkillNames())
                : context.getActiveSkillNames());
        context.setEnabledToolNames(runtimeContext != null
                ? runtimeContext.getEnabledTools().stream()
                        .map(tool -> tool.getToolDefinition().name())
                        .toList()
                : context.getEnabledToolNames());
        if (turnCompleted) {
            context.setActiveSkillNames(new ArrayList<>());
            context.setEnabledToolNames(new ArrayList<>());
            if (runtimeContext != null) {
                runtimeContext.getActiveSkillNames().clear();
            }
        }
        context.setNextMessageSortNo(context.getTurnStartSortNo() + context.getPersistedDialogueMessageIndex() + 1L);
        context.setLastActiveTime(LocalDateTime.now());
        sessionContextStore.save(context);
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
