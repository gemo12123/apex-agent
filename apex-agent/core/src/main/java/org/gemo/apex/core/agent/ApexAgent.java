package org.gemo.apex.core.agent;

import java.util.Set;
import org.gemo.apex.common.exception.CancellationRequestedException;
import org.gemo.apex.common.execution.IterationStatus;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.*;
import org.gemo.apex.common.snapshot.SessionSnapshot;
import org.gemo.apex.core.conversation.ModelRequestPreparer;
import org.gemo.apex.core.event.AgentEventEmitter;
import org.gemo.apex.core.event.AgentEventFactory;
import org.gemo.apex.core.exception.InvalidHumanResponseException;
import org.gemo.apex.core.exception.ResumePersistenceException;
import org.gemo.apex.core.exception.SuspensionEventPublishException;
import org.gemo.apex.core.lifecycle.LifecycleDispatchOutcome;
import org.gemo.apex.core.lifecycle.LifecycleDispatcher;
import org.gemo.apex.core.model.ModelStepExecutor;
import org.gemo.apex.core.tool.ToolCallCoordinator;
import org.gemo.apex.core.tool.ToolResultFactory;

/**
 * 单次 Turn 的 ReAct 编排器。
 *
 * <p>负责按固定顺序驱动生命周期 Hook、模型调用和工具调用；会话快照由 {@link ApexAgentContext} 持有并在状态边界持久化，因此本类不承担跨请求并发控制。
 */
public final class ApexAgent {
    private final ApexAgentContext context;
    private final LifecycleDispatcher dispatcher;
    private final AgentEventEmitter emitter;
    private final ModelRequestPreparer preparer;
    private final ModelStepExecutor modelStep;
    private final ToolCallCoordinator tools;

    ApexAgent(ApexAgentContext context) {
        this.context = context;
        this.dispatcher = new LifecycleDispatcher();
        AgentEventFactory eventFactory = new AgentEventFactory();
        this.emitter = new AgentEventEmitter(context.ports().eventPublisher(), eventFactory);
        this.preparer = new ModelRequestPreparer(dispatcher);
        this.modelStep = new ModelStepExecutor(dispatcher, emitter, eventFactory);
        this.tools =
                new ToolCallCoordinator(dispatcher, new ToolResultFactory(), emitter, eventFactory);
    }

    /**
     * 执行或恢复一个 Turn。
     *
     * <p>恢复请求先补完挂起工具调用；随后每轮依次保存 Iteration、分发生命周期、准备模型请求、 调用模型并处理工具。任一边界发生取消、挂起或结束时立即收口并保留可恢复状态。
     */
    public AgentRunOutcome run() {
        try {
            int firstIteration = 1;
            // 恢复必须先消费原先挂起的 ToolCall，不能直接开始下一次模型调用。
            if (context.resumedRequest()) {
                ToolCallCoordinator.ToolCallsOutcome resumed = tools.resume(context);
                if (resumed instanceof ToolCallCoordinator.ToolCallsOutcome.Suspended) {
                    return new AgentRunOutcome.Suspended();
                }
                if (resumed instanceof ToolCallCoordinator.ToolCallsOutcome.Cancelled) {
                    return new AgentRunOutcome.Cancelled();
                }
                if (resumed instanceof ToolCallCoordinator.ToolCallsOutcome.EndTurn) {
                    return finalizeTurn("resume-hook", true, true);
                }
                int currentIteration =
                        context.snapshot().activeTurn().currentIteration().iterationNo();
                context.completeIteration();
                LifecycleDispatchOutcome iterationEnd = dispatchIterationEnd();
                context.save();
                if (iterationEnd instanceof LifecycleDispatchOutcome.EndTurn end) {
                    return finalizeTurn(end.reason(), true, false);
                }
                firstIteration = currentIteration + 1;
            } else {
                LifecycleDispatchOutcome turnStart =
                        dispatcher.dispatch(
                                HookPoint.TURN_START,
                                context,
                                (current, binding) ->
                                        new TurnStartContext(
                                                current.snapshot().sessionId(),
                                                binding,
                                                current.snapshot()),
                                Set.of());
                if (turnStart instanceof LifecycleDispatchOutcome.EndTurn end) {
                    return finalizeTurn(end.reason(), true, false);
                }
            }
            int maxIterations = context.definition().definition().prompt().maxIterations();
            // 一次 Iteration 只对应一次模型调用及其返回的全部工具调用。
            for (int iterationNo = firstIteration; iterationNo <= maxIterations; iterationNo++) {
                context.ports().cancellationToken().throwIfCancellationRequested();
                context.startIteration(iterationNo);
                context.save();
                LifecycleDispatchOutcome iterationStart =
                        dispatcher.dispatch(
                                HookPoint.ITERATION_START,
                                context,
                                (current, binding) ->
                                        new IterationStartContext(
                                                current.snapshot().sessionId(),
                                                binding,
                                                current.snapshot().activeTurn()),
                                Set.of());
                if (iterationStart instanceof LifecycleDispatchOutcome.EndTurn end) {
                    return finalizeTurn(end.reason(), true, true);
                }
                ModelRequestPreparer.PreparationOutcome prepared =
                        preparer.prepare(context, iterationNo == maxIterations);
                if (prepared instanceof ModelRequestPreparer.PreparationOutcome.EndTurn end) {
                    return finalizeTurn(end.reason(), true, true);
                }
                var model =
                        modelStep.execute(
                                context,
                                ((ModelRequestPreparer.PreparationOutcome.Prepared) prepared)
                                        .request());
                if (model instanceof ModelStepExecutor.ModelStepOutcome.EndTurn end) {
                    return finalizeTurn(end.reason(), true, true);
                }
                if (model instanceof ModelStepExecutor.ModelStepOutcome.FinalText) {
                    return finalizeTurn("normal", false, true);
                }
                var calls = ((ModelStepExecutor.ModelStepOutcome.ToolCalls) model).calls();
                if (iterationNo == maxIterations) {
                    tools.forceEnd(context, calls);
                    return finalizeTurn("max-iterations", true, true);
                }
                ToolCallCoordinator.ToolCallsOutcome toolOutcome = tools.process(context, calls);
                if (toolOutcome instanceof ToolCallCoordinator.ToolCallsOutcome.Cancelled) {
                    return new AgentRunOutcome.Cancelled();
                }
                if (toolOutcome instanceof ToolCallCoordinator.ToolCallsOutcome.Suspended) {
                    return new AgentRunOutcome.Suspended();
                }
                if (toolOutcome instanceof ToolCallCoordinator.ToolCallsOutcome.EndTurn) {
                    return finalizeTurn("hook", true, true);
                }
                context.completeIteration();
                LifecycleDispatchOutcome iterationEnd = dispatchIterationEnd();
                context.save();
                if (iterationEnd instanceof LifecycleDispatchOutcome.EndTurn end) {
                    return finalizeTurn(end.reason(), true, false);
                }
            }
            throw new IllegalStateException("ReAct 循环未在最大轮次内收口");
        } catch (InvalidHumanResponseException error) {
            return new AgentRunOutcome.Failed(error);
        } catch (SuspensionEventPublishException | ResumePersistenceException error) {
            return new AgentRunOutcome.Failed(error);
        } catch (CancellationRequestedException cancellation) {
            context.cancel();
            bestEffortSave(cancellation);
            return new AgentRunOutcome.Cancelled();
        } catch (RuntimeException error) {
            context.fail();
            bestEffortSave(error);
            return new AgentRunOutcome.Failed(error);
        } finally {
            emitter.requestEnd();
        }
    }

    /** 在任务尚未提交线程池时取消，避免占用模型或工具资源。 */
    public AgentRunOutcome cancelBeforeRun() {
        try {
            context.cancel();
            context.ports().sessionRepository().save(context.snapshot());
            return new AgentRunOutcome.Cancelled();
        } catch (RuntimeException error) {
            return new AgentRunOutcome.Failed(error);
        }
    }

    public SessionSnapshot snapshot() {
        return context.snapshot();
    }

    /** 统一完成 Turn：必要时先完成当前 Iteration，再分发 TURN_END 并落库。 */
    private AgentRunOutcome finalizeTurn(
            String reason, boolean endedByHook, boolean finishIteration) {
        if (finishIteration
                && context.snapshot().activeTurn().currentIteration() != null
                && context.snapshot().activeTurn().currentIteration().status()
                        == IterationStatus.IN_PROGRESS) {
            context.completeIteration();
            LifecycleDispatchOutcome iterationEnd = dispatchIterationEnd();
            if (iterationEnd instanceof LifecycleDispatchOutcome.EndTurn end) {
                reason = end.reason();
                endedByHook = true;
            }
        }
        dispatcher.dispatch(
                HookPoint.TURN_END,
                context,
                (current, binding) ->
                        new TurnEndContext(
                                current.snapshot().sessionId(),
                                binding,
                                current.snapshot().activeTurn()),
                Set.of());
        context.completeTurn(endedByHook);
        context.save();
        return endedByHook
                ? new AgentRunOutcome.EndedByHook(reason)
                : new AgentRunOutcome.Completed();
    }

    private LifecycleDispatchOutcome dispatchIterationEnd() {
        return dispatcher.dispatch(
                HookPoint.ITERATION_END,
                context,
                (current, binding) ->
                        new IterationEndContext(
                                current.snapshot().sessionId(),
                                binding,
                                current.snapshot().activeTurn().currentIteration()),
                Set.of());
    }

    /** 失败路径尽力保存终态；保存错误作为原始异常的 suppressed 信息保留。 */
    private void bestEffortSave(RuntimeException primary) {
        try {
            context.ports().sessionRepository().save(context.snapshot());
        } catch (RuntimeException saveError) {
            primary.addSuppressed(saveError);
        }
    }
}
