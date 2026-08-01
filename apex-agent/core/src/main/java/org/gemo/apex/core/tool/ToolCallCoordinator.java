package org.gemo.apex.core.tool;

import org.gemo.apex.common.exception.CancellationRequestedException;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.PostToolCallContext;
import org.gemo.apex.common.hook.context.PreToolCallContext;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.tool.*;
import org.gemo.apex.core.agent.ApexAgentContext;
import org.gemo.apex.core.event.AgentEventEmitter;
import org.gemo.apex.core.exception.SessionStateException;
import org.gemo.apex.core.exception.ToolContractException;
import org.gemo.apex.core.lifecycle.LifecycleDispatchOutcome;
import org.gemo.apex.core.lifecycle.LifecycleDispatcher;
import org.gemo.apex.extension.tool.AgentTool;

import java.util.*;

public final class ToolCallCoordinator {
    private final LifecycleDispatcher dispatcher;
    private final ToolResultFactory results;
    private final AgentEventEmitter emitter;

    public ToolCallCoordinator(LifecycleDispatcher dispatcher, ToolResultFactory results,
                               AgentEventEmitter emitter) {
        this.dispatcher = dispatcher;
        this.results = results;
        this.emitter = emitter;
    }

    public ToolCallsOutcome process(ApexAgentContext context, List<ToolCall> calls) {
        List<ToolResult> completed = new ArrayList<>();
        for (int index = 0; index < calls.size(); index++) {
            ToolCall original = calls.get(index);
            context.toolCall(original);
            String invocationId = context.ports().idGenerator().newInvocationId();
            try {
                context.ports().cancellationToken().throwIfCancellationRequested();
                LifecycleDispatchOutcome pre = dispatcher.dispatch(HookPoint.PRE_TOOL_CALL, context,
                        current -> new PreToolCallContext(current.snapshot().sessionId(), current.toolCall(),
                                invocationId, current.ports().idGenerator().newConfirmationId()), Set.of());
                if (pre instanceof LifecycleDispatchOutcome.HumanIntervention) {
                    throw new SessionStateException("人工介入挂起需等待 CORE-07A 与 KIT-01/02 联调");
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
                        current -> new PostToolCallContext(current.snapshot().sessionId(), current.toolCall(),
                                current.toolResult()), Set.of());
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

    public void forceEnd(ApexAgentContext context, List<ToolCall> calls) {
        commitBatch(context, calls.stream().map(results::forcedEnd).toList());
    }

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
                context.snapshot().userId(), null, null, context.ports().cancellationToken(),
                Map.of("invocationId", invocationId));
        try {
            context.ports().cancellationToken().throwIfCancellationRequested();
            ToolResult result = tool.execute(call, executionContext,
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
        context.addToolResults(List.of(result));
        context.save();
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
        Map<String, Object> payload = Map.of("toolCallId", result.toolCallId(),
                "toolName", result.toolName(), "metadata", result.metadata());
        return new AgentMessageEntry(context.ports().idGenerator().newEntryId(),
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

    public sealed interface ToolCallsOutcome {
        record Completed() implements ToolCallsOutcome {}
        record Suspended() implements ToolCallsOutcome {}
        record EndTurn() implements ToolCallsOutcome {}
        record Cancelled() implements ToolCallsOutcome {}
    }
}
