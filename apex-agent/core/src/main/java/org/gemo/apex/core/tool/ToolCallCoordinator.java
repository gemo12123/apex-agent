package org.gemo.apex.core.tool;

import org.gemo.apex.common.exception.CancellationRequestedException;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.PostToolCallContext;
import org.gemo.apex.common.hook.context.PreToolCallContext;
import org.gemo.apex.common.intervention.*;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.snapshot.PreparedToolCallDisposition;
import org.gemo.apex.common.snapshot.PreparedToolCallSnapshot;
import org.gemo.apex.common.snapshot.SuspendedToolBatch;
import org.gemo.apex.common.tool.*;
import org.gemo.apex.core.agent.ApexAgentContext;
import org.gemo.apex.core.event.AgentEventEmitter;
import org.gemo.apex.core.event.AgentEventFactory;
import org.gemo.apex.core.exception.InvalidHumanResponseException;
import org.gemo.apex.core.exception.ResumePersistenceException;
import org.gemo.apex.core.exception.ToolContractException;
import org.gemo.apex.core.intervention.HumanResponseParser;
import org.gemo.apex.core.intervention.InterventionSuspender;
import org.gemo.apex.core.lifecycle.LifecycleDispatchOutcome;
import org.gemo.apex.core.lifecycle.LifecycleDispatcher;
import org.gemo.apex.core.lifecycle.PreToolDispatchOutcome;
import org.gemo.apex.core.skill.SkillActivationCoordinator;
import org.gemo.apex.extension.tool.AgentTool;

import java.util.*;

/** 对同一模型响应先完成整批 PRE_TOOL_CALL，再统一挂起或按原顺序执行。 */
public final class ToolCallCoordinator {
    private final LifecycleDispatcher dispatcher;
    private final ToolResultFactory results;
    private final AgentEventEmitter emitter;
    private final InterventionSuspender suspender;
    private final HumanResponseParser responses = new HumanResponseParser();
    private final SkillActivationCoordinator skillActivation = new SkillActivationCoordinator();

    public ToolCallCoordinator(LifecycleDispatcher dispatcher, ToolResultFactory results,
                               AgentEventEmitter emitter, AgentEventFactory eventFactory) {
        this.dispatcher = dispatcher;
        this.results = results;
        this.emitter = emitter;
        this.suspender = new InterventionSuspender(emitter, eventFactory);
    }

    public ToolCallsOutcome process(ApexAgentContext context, List<ToolCall> calls) {
        List<PreparedToolCallSnapshot> prepared = new ArrayList<>(calls.size());
        try {
            for (ToolCall call : calls) {
                context.ports().cancellationToken().throwIfCancellationRequested();
                String invocationId = context.ports().idGenerator().newInvocationId();
                PreparationOutcome outcome = prepare(context, call, invocationId, List.of(), null, false);
                if (outcome instanceof PreparationOutcome.EndTurn) {
                    commitBatch(context, calls.stream().map(results::forcedEnd).toList());
                    return new ToolCallsOutcome.EndTurn();
                }
                prepared.add(((PreparationOutcome.Prepared) outcome).snapshot());
            }
            if (hasIntervention(prepared)) {
                suspender.suspend(context, batch(context, prepared), false);
                return new ToolCallsOutcome.Suspended();
            }
            return consume(context, prepared, false);
        } catch (CancellationRequestedException cancellation) {
            commitCancelled(context, calls.stream().map(results::cancelled).toList());
            return new ToolCallsOutcome.Cancelled();
        }
    }

    public ToolCallsOutcome resume(ApexAgentContext context) {
        SuspendedToolBatch suspended = Objects.requireNonNull(
                context.snapshot().suspendedToolBatch(), "suspendedToolBatch");
        List<ToolCall> modelCalls = context.snapshot().activeTurn().currentIteration()
                .modelResponse().toolCalls();
        List<PreparedToolCallSnapshot> prepared = new ArrayList<>(suspended.toolCalls().size());
        try {
            context.ports().cancellationToken().throwIfCancellationRequested();
            validateResponseKeys(context.humanResponses(), suspended.toolCalls());
            for (int index = 0; index < suspended.toolCalls().size(); index++) {
                PreparedToolCallSnapshot current = suspended.toolCalls().get(index);
                if (current.disposition() != PreparedToolCallDisposition.INTERVENTION) {
                    prepared.add(current);
                    continue;
                }
                ToolCall call = call(current, modelCalls.get(index));
                HumanSubmission submission = responses.parse(
                        context.humanResponses().get(current.toolCallId()), current.intervention());
                context.toolCall(call);
                context.humanSubmission(submission);
                if (submission instanceof ToolConfirmationSubmission confirmation
                        && confirmation.decision() == ConfirmationDecision.DENY) {
                    prepared.add(returned(current, results.userDenied(call), submission));
                    continue;
                }
                ToolCall approved = mergeApprovedArguments(call, current.intervention(), submission);
                PreparationOutcome outcome = prepare(context, approved, current.invocationId(),
                        current.executedPreToolHookIds(), submission, true);
                if (outcome instanceof PreparationOutcome.EndTurn) {
                    context.resumeFromSuspension();
                    commitBatch(context, modelCalls.stream().map(results::forcedEnd).toList());
                    return new ToolCallsOutcome.EndTurn();
                }
                prepared.add(((PreparationOutcome.Prepared) outcome).snapshot());
            }
            if (hasIntervention(prepared)) {
                suspender.suspend(context, batch(context, prepared), true);
                return new ToolCallsOutcome.Suspended();
            }
            context.resumeFromSuspension();
            return consume(context, prepared, true);
        } catch (CancellationRequestedException cancellation) {
            context.resumeFromSuspension();
            commitCancelled(context, modelCalls.stream().map(results::cancelled).toList());
            return new ToolCallsOutcome.Cancelled();
        }
    }

    public void forceEnd(ApexAgentContext context, List<ToolCall> calls) {
        commitBatch(context, calls.stream().map(results::forcedEnd).toList());
    }

    private PreparationOutcome prepare(ApexAgentContext context, ToolCall call, String invocationId,
                                       List<String> executedHookIds, HumanSubmission submission,
                                       boolean revalidateAvailability) {
        context.toolCall(call);
        context.humanSubmission(submission);
        if (revalidateAvailability && isKnownUnavailable(context, call.name())) {
            context.migrateUnavailableTool(call.name());
            return prepared(snapshot(context, invocationId, executedHookIds,
                    PreparedToolCallDisposition.RETURN_RESULT, results.unavailable(call), null, submission));
        }
        PreToolDispatchOutcome dispatched = dispatcher.dispatchPreTool(context,
                (current, binding) -> new PreToolCallContext(current.snapshot().sessionId(), binding,
                        current.toolCall(), invocationId,
                        current.ports().idGenerator().newConfirmationId(),
                        current.currentHumanSubmission()), executedHookIds);
        LifecycleDispatchOutcome outcome = dispatched.outcome();
        if (outcome instanceof LifecycleDispatchOutcome.EndTurn end) {
            return new PreparationOutcome.EndTurn(end.reason());
        }
        if (outcome instanceof LifecycleDispatchOutcome.HumanIntervention intervention) {
            return prepared(snapshot(context, invocationId, dispatched.executedBindingIds(),
                    PreparedToolCallDisposition.INTERVENTION, null, intervention.request(), submission));
        }
        if (outcome instanceof LifecycleDispatchOutcome.BlockTool blocked) {
            return prepared(snapshot(context, invocationId, dispatched.executedBindingIds(),
                    PreparedToolCallDisposition.RETURN_RESULT,
                    results.blocked(context.toolCall(), blocked.reason()), null, submission));
        }
        if (outcome instanceof LifecycleDispatchOutcome.DirectToolResult direct) {
            return prepared(snapshot(context, invocationId, dispatched.executedBindingIds(),
                    PreparedToolCallDisposition.RETURN_RESULT, direct.result(), null, submission));
        }
        if (!context.snapshot().enabledTools().contains(context.toolCall().name())) {
            return prepared(snapshot(context, invocationId, dispatched.executedBindingIds(),
                    PreparedToolCallDisposition.RETURN_RESULT,
                    results.disabled(context.toolCall()), null, submission));
        }
        return prepared(snapshot(context, invocationId, dispatched.executedBindingIds(),
                PreparedToolCallDisposition.EXECUTE, null, null, submission));
    }

    private ToolCallsOutcome consume(ApexAgentContext context, List<PreparedToolCallSnapshot> prepared,
                                     boolean resumed) {
        for (int index = 0; index < prepared.size(); index++) {
            PreparedToolCallSnapshot item = prepared.get(index);
            ToolCall call = call(item, context.snapshot().activeTurn().currentIteration()
                    .modelResponse().toolCalls().get(index));
            context.toolCall(call);
            context.humanSubmission(item.submission());
            try {
                context.ports().cancellationToken().throwIfCancellationRequested();
                ToolResult result = item.disposition() == PreparedToolCallDisposition.RETURN_RESULT
                        ? item.result() : execute(context, item.invocationId());
                context.toolResult(result);
                LifecycleDispatchOutcome post = dispatchPost(context);
                result = context.toolResult();
                validateAssociation(call, result);
                commitOne(context, result, resumed ? "tool-result-" + item.invocationId() : null);
                if (post instanceof LifecycleDispatchOutcome.EndTurn) {
                    List<ToolResult> forced = prepared.subList(index + 1, prepared.size()).stream()
                            .map(next -> results.forcedEnd(call(next, findModelCall(context, next)))).toList();
                    commitBatch(context, forced);
                    return new ToolCallsOutcome.EndTurn();
                }
            } catch (CancellationRequestedException cancellation) {
                List<ToolResult> cancelled = prepared.subList(index, prepared.size()).stream()
                        .map(next -> results.cancelled(call(next, findModelCall(context, next)))).toList();
                commitCancelled(context, cancelled);
                return new ToolCallsOutcome.Cancelled();
            }
        }
        return new ToolCallsOutcome.Completed();
    }

    private ToolResult execute(ApexAgentContext context, String invocationId) {
        ToolCall call = context.toolCall();
        AgentTool tool = context.toolCatalog().find(call.name());
        if (tool == null) {
            if (isKnownUnavailable(context, call.name()) || context.snapshot().historicalToolBindings().stream()
                    .anyMatch(binding -> binding.toolName().equals(call.name()))) {
                if (isKnownUnavailable(context, call.name())) context.migrateUnavailableTool(call.name());
                return results.unavailable(call);
            }
            throw new ToolContractException("模型调用了未注册工具: " + call.name());
        }
        ToolExecutionContext executionContext = new ToolExecutionContext(context.snapshot().sessionId(),
                context.snapshot().currentTurnNo(), context.snapshot().activeTurn().currentIteration().iterationNo(),
                context.snapshot().userId(), context.currentHumanSubmission(), null,
                context.ports().cancellationToken(), Map.of("invocationId", invocationId));
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

    private PreparedToolCallSnapshot snapshot(ApexAgentContext context, String invocationId,
                                              List<String> executedHookIds,
                                              PreparedToolCallDisposition disposition,
                                              ToolResult result, HumanInterventionRequest intervention,
                                              HumanSubmission submission) {
        ToolCall call = context.toolCall();
        return new PreparedToolCallSnapshot(call.toolCallId(), invocationId, call.name(), call.ordinal(),
                call.arguments(), executedHookIds, disposition, result, intervention, submission);
    }

    private PreparedToolCallSnapshot returned(PreparedToolCallSnapshot source, ToolResult result,
                                              HumanSubmission submission) {
        return new PreparedToolCallSnapshot(source.toolCallId(), source.invocationId(), source.toolName(),
                source.ordinal(), source.resolvedArguments(), source.executedPreToolHookIds(),
                PreparedToolCallDisposition.RETURN_RESULT, result, null, submission);
    }

    private PreparationOutcome prepared(PreparedToolCallSnapshot snapshot) {
        return new PreparationOutcome.Prepared(snapshot);
    }

    private SuspendedToolBatch batch(ApexAgentContext context, List<PreparedToolCallSnapshot> prepared) {
        return new SuspendedToolBatch(context.snapshot().sessionId(), context.snapshot().currentTurnNo(),
                context.snapshot().activeTurn().currentIteration().iterationNo(), prepared);
    }

    private ToolCall mergeApprovedArguments(ToolCall call, HumanInterventionRequest intervention,
                                            HumanSubmission submission) {
        if (!(submission instanceof ToolConfirmationSubmission confirmation)
                || confirmation.decision() != ConfirmationDecision.CONFIRM
                || !(intervention instanceof ToolConfirmationInterventionRequest request)) return call;
        Map<String, Object> arguments = new LinkedHashMap<>(call.arguments());
        confirmation.updatedArguments().forEach((key, value) -> {
            if (request.editableArgumentKeys().contains(key)) arguments.put(key, value);
        });
        return new ToolCall(call.toolCallId(), call.name(), call.ordinal(), arguments, call.metadata());
    }

    private ToolCall call(PreparedToolCallSnapshot prepared, ToolCall modelCall) {
        if (!prepared.toolCallId().equals(modelCall.toolCallId())
                || !prepared.toolName().equals(modelCall.name())
                || prepared.ordinal() != modelCall.ordinal()) {
            throw new ToolContractException("预处理 ToolCall 与模型响应不一致");
        }
        return new ToolCall(modelCall.toolCallId(), modelCall.name(), modelCall.ordinal(),
                prepared.resolvedArguments(), modelCall.metadata());
    }

    private ToolCall findModelCall(ApexAgentContext context, PreparedToolCallSnapshot prepared) {
        return context.snapshot().activeTurn().currentIteration().modelResponse().toolCalls().stream()
                .filter(call -> call.toolCallId().equals(prepared.toolCallId())).findFirst()
                .orElseThrow(() -> new ToolContractException("预处理 ToolCall 不存在"));
    }

    private boolean hasIntervention(List<PreparedToolCallSnapshot> prepared) {
        return prepared.stream().anyMatch(item ->
                item.disposition() == PreparedToolCallDisposition.INTERVENTION);
    }

    private void validateResponseKeys(Map<String, Object> response,
                                      List<PreparedToolCallSnapshot> prepared) {
        Set<String> expected = new HashSet<>();
        prepared.stream().filter(item -> item.disposition() == PreparedToolCallDisposition.INTERVENTION)
                .map(PreparedToolCallSnapshot::toolCallId).forEach(expected::add);
        for (String key : response.keySet()) {
            if (!expected.contains(key)) throw new InvalidHumanResponseException("未知 tool_call_id: " + key);
        }
    }

    private void commitOne(ApexAgentContext context, ToolResult result, String stableEntryId) {
        try {
            AgentMessageEntry entry = entry(context, result,
                    stableEntryId == null ? context.ports().idGenerator().newEntryId() : stableEntryId);
            context.ports().conversationRepository().append(List.of(entry));
            context.applyPendingSkillActivation();
            context.addToolResults(List.of(result));
            context.save();
        } catch (RuntimeException error) {
            if (stableEntryId != null) throw new ResumePersistenceException(error);
            throw error;
        }
    }

    private void commitBatch(ApexAgentContext context, List<ToolResult> batch) {
        if (batch.isEmpty()) return;
        List<AgentMessageEntry> entries = batch.stream().map(result -> entry(context, result,
                context.ports().idGenerator().newEntryId())).toList();
        context.ports().conversationRepository().append(entries);
        context.addToolResults(batch);
        context.save();
    }

    private void commitCancelled(ApexAgentContext context, List<ToolResult> batch) {
        List<AgentMessageEntry> entries = batch.stream().map(result -> entry(context, result,
                context.ports().idGenerator().newEntryId())).toList();
        context.ports().conversationRepository().append(entries);
        context.addToolResults(batch);
        context.cancel();
        context.ports().sessionRepository().save(context.snapshot());
    }

    private AgentMessageEntry entry(ApexAgentContext context, ToolResult result, String entryId) {
        Map<String, Object> payload = Map.of("toolCallId", result.toolCallId(),
                "toolName", result.toolName(), "metadata", result.metadata());
        return new AgentMessageEntry(entryId, context.snapshot().sessionId(),
                context.snapshot().currentTurnNo(), context.allocateSortNo(), MessageRole.TOOL,
                MessageType.TOOL_RESULT, result.content(), payload, context.ports().timeProvider().now());
    }

    private LifecycleDispatchOutcome dispatchPost(ApexAgentContext context) {
        return dispatcher.dispatch(HookPoint.POST_TOOL_CALL, context,
                (current, binding) -> new PostToolCallContext(current.snapshot().sessionId(), binding,
                        current.toolCall(), current.toolResult()), Set.of());
    }

    private boolean isKnownUnavailable(ApexAgentContext context, String toolName) {
        if (context.toolCatalog().contains(toolName)) return false;
        var availability = context.ports().toolAvailabilityProvider().current();
        return availability.unavailableToolNames().contains(toolName)
                || availability.unavailableSources().stream()
                .anyMatch(source -> toolName.startsWith(source.stableNamePrefix()));
    }

    private void validateAssociation(ToolCall call, ToolResult result) {
        if (result == null || !call.toolCallId().equals(result.toolCallId())
                || !call.name().equals(result.toolName())) {
            throw new ToolContractException("ToolResult 与 ToolCall ID/name 不一致");
        }
    }

    private sealed interface PreparationOutcome {
        record Prepared(PreparedToolCallSnapshot snapshot) implements PreparationOutcome {}
        record EndTurn(String reason) implements PreparationOutcome {}
    }

    public sealed interface ToolCallsOutcome {
        record Completed() implements ToolCallsOutcome {}
        record Suspended() implements ToolCallsOutcome {}
        record EndTurn() implements ToolCallsOutcome {}
        record Cancelled() implements ToolCallsOutcome {}
    }
}
