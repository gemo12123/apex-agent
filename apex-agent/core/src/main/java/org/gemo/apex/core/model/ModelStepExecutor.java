package org.gemo.apex.core.model;

import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.PostModelCallContext;
import org.gemo.apex.common.hook.context.PreModelCallContext;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.*;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.core.agent.ApexAgentContext;
import org.gemo.apex.core.event.AgentEventEmitter;
import org.gemo.apex.core.event.AgentEventFactory;
import org.gemo.apex.core.exception.ModelContextLimitException;
import org.gemo.apex.core.lifecycle.LifecycleDispatchOutcome;
import org.gemo.apex.core.lifecycle.LifecycleDispatcher;
import org.gemo.apex.extension.model.ModelStreamObserver;

import java.util.*;

public final class ModelStepExecutor {
    private final LifecycleDispatcher dispatcher;
    private final AgentEventEmitter emitter;
    private final AgentEventFactory events;

    public ModelStepExecutor(LifecycleDispatcher dispatcher, AgentEventEmitter emitter,
                             AgentEventFactory events) {
        this.dispatcher = dispatcher;
        this.emitter = emitter;
        this.events = events;
    }

    public ModelStepOutcome execute(ApexAgentContext context, ModelRequest base) {
        context.modelRequest(base);
        LifecycleDispatchOutcome pre = dispatcher.dispatch(HookPoint.PRE_MODEL_CALL, context,
                (current, binding) -> new PreModelCallContext(current.snapshot().sessionId(), binding,
                        current.modelRequest()), Set.of());
        if (pre instanceof LifecycleDispatchOutcome.EndTurn end) return new ModelStepOutcome.EndTurn(end.reason());
        validateHardLimit(context.modelRequest(), context.ports().modelRequestHardLimit());
        String contentId = context.ports().idGenerator().newInvocationId();
        context.ports().cancellationToken().throwIfCancellationRequested();
        ModelResponse response = context.ports().modelGateway().stream(context.modelRequest(),
                new ModelStreamObserver() {
                    @Override public void onChunk(ModelStreamChunk chunk) {
                        context.ports().cancellationToken().throwIfCancellationRequested();
                        if (chunk.textDelta() != null && !chunk.textDelta().isEmpty()) {
                            emitter.publish(events.streamContent(contentId, chunk.textDelta()));
                        }
                    }
                    @Override public org.gemo.apex.common.tool.CancellationToken cancellationToken() {
                        return context.ports().cancellationToken();
                    }
                });
        context.ports().cancellationToken().throwIfCancellationRequested();
        context.modelResponse(response);
        LifecycleDispatchOutcome post = dispatcher.dispatch(HookPoint.POST_MODEL_CALL, context,
                (current, binding) -> new PostModelCallContext(current.snapshot().sessionId(), binding,
                        current.modelResponse()), Set.of());
        if (post instanceof LifecycleDispatchOutcome.EndTurn end) return new ModelStepOutcome.EndTurn(end.reason());
        commitAssistant(context);
        return context.modelResponse().toolCalls().isEmpty()
                ? new ModelStepOutcome.FinalText(context.modelResponse().text())
                : new ModelStepOutcome.ToolCalls(context.modelResponse().toolCalls());
    }

    private void validateHardLimit(ModelRequest request, long limit) {
        long actual = request.systemPrompt().length()
                + request.messages().stream().mapToLong(message ->
                (message.content() == null ? 0 : message.content().length()) + message.payload().toString().length()).sum()
                + request.tools().stream().mapToLong(tool -> tool.description().length()
                + tool.inputSchemaJson().length()).sum();
        if (actual > limit) throw new ModelContextLimitException(actual, limit);
    }

    private void commitAssistant(ApexAgentContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (!context.modelResponse().toolCalls().isEmpty()) {
            payload.put("toolCalls", context.modelResponse().toolCalls().stream().map(this::toolCallMap).toList());
        }
        AgentMessageEntry entry = new AgentMessageEntry(context.ports().idGenerator().newEntryId(),
                context.snapshot().sessionId(), context.snapshot().currentTurnNo(), context.allocateSortNo(),
                MessageRole.ASSISTANT, context.modelResponse().toolCalls().isEmpty()
                ? MessageType.TEXT : MessageType.TOOL_CALLS, context.modelResponse().text(), payload,
                context.ports().timeProvider().now());
        context.ports().conversationRepository().append(List.of(entry));
        context.updateIteration(context.modelRequest(), context.modelResponse(), List.of(),
                org.gemo.apex.common.execution.IterationStatus.IN_PROGRESS, null);
        context.save();
    }

    private Map<String, Object> toolCallMap(ToolCall call) {
        return Map.of("toolCallId", call.toolCallId(), "name", call.name(),
                "ordinal", call.ordinal(), "arguments", call.arguments(), "metadata", call.metadata());
    }

    public sealed interface ModelStepOutcome {
        record FinalText(String text) implements ModelStepOutcome {}
        record ToolCalls(List<ToolCall> calls) implements ModelStepOutcome {
            public ToolCalls { calls = List.copyOf(calls); }
        }
        record EndTurn(String reason) implements ModelStepOutcome {}
    }
}
