package org.gemo.apex.core.tool;

import org.gemo.apex.common.exception.CancellationRequestedException;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.PostToolCallContext;
import org.gemo.apex.common.hook.context.PreToolCallContext;
import org.gemo.apex.common.intervention.ConfirmationDecision;
import org.gemo.apex.common.intervention.ToolConfirmationInterventionRequest;
import org.gemo.apex.common.intervention.ToolConfirmationSubmission;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.tool.*;
import org.gemo.apex.core.agent.ApexAgentContext;
import org.gemo.apex.core.event.AgentEventEmitter;
import org.gemo.apex.core.event.AgentEventFactory;
import org.gemo.apex.core.intervention.InterventionSuspender;
import org.gemo.apex.core.exception.ToolContractException;
import org.gemo.apex.core.exception.ResumePersistenceException;
import org.gemo.apex.core.lifecycle.LifecycleDispatchOutcome;
import org.gemo.apex.core.lifecycle.LifecycleDispatcher;
import org.gemo.apex.core.lifecycle.PreToolDispatchOutcome;
import org.gemo.apex.core.skill.SkillActivationCoordinator;
import org.gemo.apex.extension.tool.AgentTool;

import java.util.*;

/**
 * 顺序执行同一模型响应中的 ToolCall，并维持 ToolCall/ToolResult 的一一配对。
 *
 * <p>人工介入会中断批次：先写入挂起快照，再发布交互事件；恢复时只继续原批次中尚未完成的调用。</p>
 */
public final class ToolCallCoordinator {
    private final LifecycleDispatcher dispatcher;
    private final ToolResultFactory results;
    private final AgentEventEmitter emitter;
    private final InterventionSuspender suspender;
    private final SkillActivationCoordinator skillActivation = new SkillActivationCoordinator();

    public ToolCallCoordinator(LifecycleDispatcher dispatcher, ToolResultFactory results,
                               AgentEventEmitter emitter, AgentEventFactory eventFactory) {
        this.dispatcher = dispatcher;
        this.results = results;
        this.emitter = emitter;
        this.suspender = new InterventionSuspender(emitter, eventFactory);
    }

    /**
     * 处理新模型响应的工具调用。每个调用前后均经过 Hook，已完成结果会立即提交，
     * 从而使挂起或异常时的快照仍保持一致。
     */
    public ToolCallsOutcome process(ApexAgentContext context, List<ToolCall> calls) {
        List<ToolResult> completed = new ArrayList<>();
        for (int index = 0; index < calls.size(); index++) {
            ToolCall original = calls.get(index);
            context.toolCall(original);
            String invocationId = context.ports().idGenerator().newInvocationId();
            try {
                context.ports().cancellationToken().throwIfCancellationRequested();
                PreToolDispatchOutcome dispatched = dispatcher.dispatchPreTool(context,
                        (current, binding) -> new PreToolCallContext(current.snapshot().sessionId(), binding,
                                current.toolCall(), invocationId,
                                current.ports().idGenerator().newConfirmationId(),
                                current.currentHumanSubmission()), List.of());
                LifecycleDispatchOutcome pre = dispatched.outcome();
                if (pre instanceof LifecycleDispatchOutcome.HumanIntervention) {
                    suspender.suspend(context, invocationId,
                            ((LifecycleDispatchOutcome.HumanIntervention) pre).request(),
                            dispatched.executedBindingIds(), false);
                    return new ToolCallsOutcome.Suspended();
                }
                if (pre instanceof LifecycleDispatchOutcome.EndTurn) {
                    List<ToolResult> forced = forced(calls, index);
                    commitBatch(context, forced);
                    return new ToolCallsOutcome.EndTurn();
                }
                ToolResult result;
                if (pre instanceof LifecycleDispatchOutcome.BlockTool blocked) {
                    result = results.blocked(context.toolCall(), blocked.reason());
                } else if (pre instanceof LifecycleDispatchOutcome.DirectToolResult direct) {
                    result = direct.result();
                } else {
                    result = execute(context, invocationId);
                }
                context.toolResult(result);
                LifecycleDispatchOutcome post = dispatcher.dispatch(HookPoint.POST_TOOL_CALL, context,
                        (current, binding) -> new PostToolCallContext(current.snapshot().sessionId(), binding,
                                current.toolCall(), current.toolResult()), Set.of());
                result = context.toolResult();
                validateAssociation(context.toolCall(), result);
                commitOne(context, result);
                completed.add(result);
                if (post instanceof LifecycleDispatchOutcome.EndTurn) {
                    List<ToolResult> forced = forced(calls, index + 1);
                    commitBatch(context, forced);
                    return new ToolCallsOutcome.EndTurn();
                }
            } catch (CancellationRequestedException cancellation) {
                List<ToolResult> cancelled = calls.subList(index, calls.size()).stream()
                        .map(results::cancelled).toList();
                commitCancelled(context, cancelled);
                return new ToolCallsOutcome.Cancelled();
            }
        }
        return new ToolCallsOutcome.Completed();
    }

    /**
     * 将人工提交结果绑定到挂起调用，再按原顺序继续余下调用。
     */
    public ToolCallsOutcome resume(ApexAgentContext context) {
        context.ports().cancellationToken().throwIfCancellationRequested();
        var suspended = Objects.requireNonNull(context.snapshot().suspendedToolCall(), "suspendedToolCall");
        List<ToolCall> calls = context.snapshot().activeTurn().currentIteration().modelResponse().toolCalls();
        int index = -1;
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).toolCallId().equals(suspended.toolCallId())) {
                if (index >= 0) throw new ToolContractException("挂起 ToolCall ID 不唯一");
                index = i;
            }
        }
        if (index < 0) throw new ToolContractException("挂起 ToolCall 不存在");
        ToolCall original = calls.get(index);
        ToolCall resumedCall = mergeApprovedArguments(context, original, suspended);
        context.toolCall(resumedCall);

        LifecycleDispatchOutcome pre;
        List<String> executedIds = suspended.executedPreToolHookIds();
        if (context.currentHumanSubmission() instanceof ToolConfirmationSubmission confirmation
                && confirmation.decision() == ConfirmationDecision.DENY) {
            pre = new LifecycleDispatchOutcome.DirectToolResult(results.userDenied(resumedCall));
        } else if (isKnownUnavailable(context, resumedCall.name())) {
            context.migrateUnavailableTool(resumedCall.name());
            pre = new LifecycleDispatchOutcome.DirectToolResult(results.unavailable(resumedCall));
        } else {
            PreToolDispatchOutcome dispatched = dispatcher.dispatchPreTool(context,
                    (current, binding) -> new PreToolCallContext(current.snapshot().sessionId(), binding,
                            current.toolCall(), suspended.invocationId(),
                            current.ports().idGenerator().newConfirmationId(),
                            current.currentHumanSubmission()), executedIds);
            pre = dispatched.outcome();
            executedIds = dispatched.executedBindingIds();
            if (pre instanceof LifecycleDispatchOutcome.HumanIntervention intervention) {
                suspender.suspend(context, suspended.invocationId(), intervention.request(), executedIds, true);
                return new ToolCallsOutcome.Suspended();
            }
        }

        if (pre instanceof LifecycleDispatchOutcome.EndTurn) {
            context.resumeFromSuspension();
            commitBatch(context, forced(calls, index));
            return new ToolCallsOutcome.EndTurn();
        }
        ToolResult result;
        if (pre instanceof LifecycleDispatchOutcome.BlockTool blocked) {
            result = results.blocked(resumedCall, blocked.reason());
        } else if (pre instanceof LifecycleDispatchOutcome.DirectToolResult direct) {
            result = direct.result();
        } else {
            result = execute(context, suspended.invocationId());
        }
        context.toolResult(result);
        LifecycleDispatchOutcome post = dispatchPost(context);
        result = context.toolResult();
        validateAssociation(context.toolCall(), result);
        commitResumedOne(context, result, suspended.invocationId());
        if (post instanceof LifecycleDispatchOutcome.EndTurn) {
            commitBatch(context, forced(calls, index + 1));
            return new ToolCallsOutcome.EndTurn();
        }
        if (index + 1 == calls.size()) return new ToolCallsOutcome.Completed();
        return process(context, calls.subList(index + 1, calls.size()));
    }

    /** 最大轮次到达时为未执行调用生成固定结束结果，避免留下无配对的 ToolCall。 */
    public void forceEnd(ApexAgentContext context, List<ToolCall> calls) {
        commitBatch(context, calls.stream().map(results::forcedEnd).toList());
    }

    /** 执行当前上下文中的调用；Skill 激活工具由 core 协调，其余调用交给已绑定工具。 */
    private ToolResult execute(ApexAgentContext context, String invocationId) {
        ToolCall call = context.toolCall();
        if (!context.snapshot().enabledTools().contains(call.name())) return results.disabled(call);
        AgentTool tool = context.toolCatalog().find(call.name());
        if (tool == null) {
            boolean historical = context.snapshot().historicalToolBindings().stream()
                    .anyMatch(binding -> binding.toolName().equals(call.name()));
            if (historical) return results.unavailable(call);
            throw new ToolContractException("模型调用了未注册工具: " + call.name());
        }
        ToolExecutionContext executionContext = new ToolExecutionContext(context.snapshot().sessionId(),
                context.snapshot().currentTurnNo(), context.snapshot().activeTurn().currentIteration().iterationNo(),
                context.snapshot().userId(), context.currentHumanSubmission(), null,
                context.ports().cancellationToken(),
                Map.of("invocationId", invocationId));
        try {
            context.ports().cancellationToken().throwIfCancellationRequested();
            ToolResult result = SkillActivationCoordinator.TOOL_NAME.equals(call.name())
                    ? skillActivation.activate(context, call)
                    : tool.execute(call, executionContext,
                            new RestrictedToolExecutionObserver(invocationId, emitter,
                                    context.ports().cancellationToken()));
            context.ports().cancellationToken().throwIfCancellationRequested();
            validateAssociation(call, result);
            return result;
        } catch (CancellationRequestedException error) {
            throw error;
        } catch (RuntimeException error) {
            return results.executionFailed(call, error);
        }
    }

    private void commitOne(ApexAgentContext context, ToolResult result) {
        AgentMessageEntry entry = entry(context, result);
        context.ports().conversationRepository().append(List.of(entry));
        context.applyPendingSkillActivation();
        context.addToolResults(List.of(result));
        context.save();
    }

    private void commitResumedOne(ApexAgentContext context, ToolResult result, String invocationId) {
        try {
            AgentMessageEntry entry = entry(context, result, "tool-result-" + invocationId);
            context.ports().conversationRepository().append(List.of(entry));
            context.applyPendingSkillActivation();
            context.addToolResults(List.of(result));
            context.resumeFromSuspension();
            context.save();
        } catch (RuntimeException error) {
            throw new ResumePersistenceException(error);
        }
    }

    private void commitBatch(ApexAgentContext context, List<ToolResult> batch) {
        if (batch.isEmpty()) return;
        List<AgentMessageEntry> entries = batch.stream().map(result -> entry(context, result)).toList();
        context.ports().conversationRepository().append(entries);
        context.addToolResults(batch);
        context.save();
    }

    private void commitCancelled(ApexAgentContext context, List<ToolResult> batch) {
        List<AgentMessageEntry> entries = batch.stream().map(result -> entry(context, result)).toList();
        context.ports().conversationRepository().append(entries);
        context.addToolResults(batch);
        context.cancel();
        context.ports().sessionRepository().save(context.snapshot());
    }

    private AgentMessageEntry entry(ApexAgentContext context, ToolResult result) {
        return entry(context, result, context.ports().idGenerator().newEntryId());
    }

    private AgentMessageEntry entry(ApexAgentContext context, ToolResult result, String entryId) {
        Map<String, Object> payload = Map.of("toolCallId", result.toolCallId(),
                "toolName", result.toolName(), "metadata", result.metadata());
        return new AgentMessageEntry(entryId,
                context.snapshot().sessionId(), context.snapshot().currentTurnNo(), context.allocateSortNo(),
                MessageRole.TOOL, MessageType.TOOL_RESULT, result.content(), payload,
                context.ports().timeProvider().now());
    }

    private List<ToolResult> forced(List<ToolCall> calls, int start) {
        return calls.subList(start, calls.size()).stream().map(results::forcedEnd).toList();
    }

    private void validateAssociation(ToolCall call, ToolResult result) {
        if (result == null || !call.toolCallId().equals(result.toolCallId())
                || !call.name().equals(result.toolName())) {
            throw new ToolContractException("ToolResult 与 ToolCall ID/name 不一致");
        }
    }

    private LifecycleDispatchOutcome dispatchPost(ApexAgentContext context) {
        return dispatcher.dispatch(HookPoint.POST_TOOL_CALL, context,
                (current, binding) -> new PostToolCallContext(current.snapshot().sessionId(), binding,
                        current.toolCall(), current.toolResult()), Set.of());
    }

    private ToolCall mergeApprovedArguments(ApexAgentContext context, ToolCall original,
                                            org.gemo.apex.common.snapshot.SuspendedToolCall suspended) {
        Map<String, Object> arguments = new LinkedHashMap<>(suspended.resolvedArguments());
        if (context.humanSubmission() instanceof ToolConfirmationSubmission submission
                && submission.decision() == ConfirmationDecision.CONFIRM
                && suspended.intervention() instanceof ToolConfirmationInterventionRequest confirmation) {
            submission.updatedArguments().forEach((key, value) -> {
                if (confirmation.editableArgumentKeys().contains(key)) arguments.put(key, value);
            });
        }
        return new ToolCall(original.toolCallId(), original.name(), original.ordinal(), arguments,
                original.metadata());
    }

    private boolean isKnownUnavailable(ApexAgentContext context, String toolName) {
        if (context.toolCatalog().contains(toolName)) return false;
        var availability = context.ports().toolAvailabilityProvider().current();
        return availability.unavailableToolNames().contains(toolName)
                || availability.unavailableSources().stream()
                .anyMatch(source -> toolName.startsWith(source.stableNamePrefix()));
    }

    public sealed interface ToolCallsOutcome {
        record Completed() implements ToolCallsOutcome {}
        record Suspended() implements ToolCallsOutcome {}
        record EndTurn() implements ToolCallsOutcome {}
        record Cancelled() implements ToolCallsOutcome {}
    }
}
