package org.gemo.apex.core.agent;

import org.gemo.apex.common.exception.CancellationRequestedException;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.*;
import org.gemo.apex.core.conversation.ModelRequestPreparer;
import org.gemo.apex.core.event.AgentEventEmitter;
import org.gemo.apex.core.event.AgentEventFactory;
import org.gemo.apex.core.lifecycle.LifecycleDispatchOutcome;
import org.gemo.apex.core.lifecycle.LifecycleDispatcher;
import org.gemo.apex.core.model.ModelStepExecutor;
import org.gemo.apex.core.exception.SuspensionEventPublishException;
import org.gemo.apex.core.exception.ResumePersistenceException;
import org.gemo.apex.core.tool.ToolCallCoordinator;
import org.gemo.apex.core.tool.ToolResultFactory;

import java.util.Set;

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
        this.tools = new ToolCallCoordinator(dispatcher, new ToolResultFactory(), emitter, eventFactory);
    }

    public AgentRunOutcome run() {
        try {
            int firstIteration = 1;
            if (context.humanSubmission() != null) {
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
                int currentIteration = context.snapshot().activeTurn().currentIteration().iterationNo();
                context.completeIteration();
                LifecycleDispatchOutcome iterationEnd = dispatchIterationEnd();
                context.save();
                if (iterationEnd instanceof LifecycleDispatchOutcome.EndTurn end) {
                    return finalizeTurn(end.reason(), true, false);
                }
                firstIteration = currentIteration + 1;
            } else {
                LifecycleDispatchOutcome turnStart = dispatcher.dispatch(HookPoint.TURN_START, context,
                        (current, binding) -> new TurnStartContext(current.snapshot().sessionId(), binding,
                                current.snapshot()), Set.of());
                if (turnStart instanceof LifecycleDispatchOutcome.EndTurn end) {
                    return finalizeTurn(end.reason(), true, false);
                }
            }
            for (int iterationNo = firstIteration; iterationNo <= context.ports().maxIterations(); iterationNo++) {
                context.ports().cancellationToken().throwIfCancellationRequested();
                context.startIteration(iterationNo);
                context.save();
                LifecycleDispatchOutcome iterationStart = dispatcher.dispatch(HookPoint.ITERATION_START, context,
                        (current, binding) -> new IterationStartContext(current.snapshot().sessionId(), binding,
                                current.snapshot().activeTurn()), Set.of());
                if (iterationStart instanceof LifecycleDispatchOutcome.EndTurn end) {
                    return finalizeTurn(end.reason(), true, true);
                }
                ModelRequestPreparer.PreparationOutcome prepared = preparer.prepare(context,
                        iterationNo == context.ports().maxIterations());
                if (prepared instanceof ModelRequestPreparer.PreparationOutcome.EndTurn end) {
                    return finalizeTurn(end.reason(), true, true);
                }
                var model = modelStep.execute(context,
                        ((ModelRequestPreparer.PreparationOutcome.Prepared) prepared).request());
                if (model instanceof ModelStepExecutor.ModelStepOutcome.EndTurn end) {
                    return finalizeTurn(end.reason(), true, true);
                }
                if (model instanceof ModelStepExecutor.ModelStepOutcome.FinalText) {
                    return finalizeTurn("normal", false, true);
                }
                var calls = ((ModelStepExecutor.ModelStepOutcome.ToolCalls) model).calls();
                if (iterationNo == context.ports().maxIterations()) {
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

    public AgentRunOutcome cancelBeforeRun() {
        try {
            context.cancel();
            context.ports().sessionRepository().save(context.snapshot());
            return new AgentRunOutcome.Cancelled();
        } catch (RuntimeException error) {
            return new AgentRunOutcome.Failed(error);
        }
    }

    public org.gemo.apex.common.snapshot.SessionSnapshot snapshot() { return context.snapshot(); }

    private AgentRunOutcome finalizeTurn(String reason, boolean endedByHook,
                                         boolean finishIteration) {
        if (finishIteration && context.snapshot().activeTurn().currentIteration() != null
                && context.snapshot().activeTurn().currentIteration().status()
                == org.gemo.apex.common.execution.IterationStatus.IN_PROGRESS) {
            context.completeIteration();
            LifecycleDispatchOutcome iterationEnd = dispatchIterationEnd();
            if (iterationEnd instanceof LifecycleDispatchOutcome.EndTurn end) {
                reason = end.reason();
                endedByHook = true;
            }
        }
        dispatcher.dispatch(HookPoint.TURN_END, context,
                (current, binding) -> new TurnEndContext(current.snapshot().sessionId(), binding,
                        current.snapshot().activeTurn()), Set.of());
        context.completeTurn(endedByHook);
        context.save();
        return endedByHook ? new AgentRunOutcome.EndedByHook(reason) : new AgentRunOutcome.Completed();
    }

    private LifecycleDispatchOutcome dispatchIterationEnd() {
        return dispatcher.dispatch(HookPoint.ITERATION_END, context,
                (current, binding) -> new IterationEndContext(current.snapshot().sessionId(), binding,
                        current.snapshot().activeTurn().currentIteration()), Set.of());
    }

    private void bestEffortSave(RuntimeException primary) {
        try {
            context.ports().sessionRepository().save(context.snapshot());
        } catch (RuntimeException saveError) {
            primary.addSuppressed(saveError);
        }
    }
}
