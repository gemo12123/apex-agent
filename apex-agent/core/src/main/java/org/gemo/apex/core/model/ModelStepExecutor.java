package org.gemo.apex.core.model;

import java.util.*;
import org.gemo.apex.common.exception.CancellationRequestedException;
import org.gemo.apex.common.execution.IterationStatus;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.PostModelCallContext;
import org.gemo.apex.common.hook.context.PreModelCallContext;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.*;
import org.gemo.apex.common.tool.CancellationToken;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.core.agent.ApexAgentContext;
import org.gemo.apex.core.event.AgentEventEmitter;
import org.gemo.apex.core.event.AgentEventFactory;
import org.gemo.apex.core.exception.ModelInvocationException;
import org.gemo.apex.core.lifecycle.LifecycleDispatchOutcome;
import org.gemo.apex.core.lifecycle.LifecycleDispatcher;
import org.gemo.apex.extension.model.ModelStreamObserver;

/** 执行一次模型步骤，并把流式文本和最终模型响应接入 Agent 状态。 */
public final class ModelStepExecutor {
    private static final System.Logger LOG = System.getLogger(ModelStepExecutor.class.getName());
    private static final int MODEL_RETRY_COUNT = 3;
    private final LifecycleDispatcher dispatcher;
    private final AgentEventEmitter emitter;
    private final AgentEventFactory events;

    public ModelStepExecutor(
            LifecycleDispatcher dispatcher, AgentEventEmitter emitter, AgentEventFactory events) {
        this.dispatcher = dispatcher;
        this.emitter = emitter;
        this.events = events;
    }

    /** 分发 PRE_MODEL_CALL、校验请求上限、转发流式文本、分发 POST_MODEL_CALL，最后持久化助手消息。 */
    public ModelStepOutcome execute(ApexAgentContext context, ModelRequest base) {
        context.modelRequest(base);
        LifecycleDispatchOutcome pre =
                dispatcher.dispatch(
                        HookPoint.PRE_MODEL_CALL,
                        context,
                        (current, binding) ->
                                new PreModelCallContext(
                                        current.snapshot().sessionId(),
                                        binding,
                                        current.modelRequest(),
                                        current.sharedData()),
                        Set.of());
        if (pre instanceof LifecycleDispatchOutcome.EndTurn end) {
            return new ModelStepOutcome.EndTurn(end.reason());
        }
        String contentId = context.ports().idGenerator().newInvocationId();
        context.ports().cancellationToken().throwIfCancellationRequested();
        // 流式分片只向客户端转发；完整响应仍由 gateway 返回后一次性进入快照。
        ModelResponse response = invokeModel(context, contentId);
        context.ports().cancellationToken().throwIfCancellationRequested();
        context.modelResponse(response);
        LifecycleDispatchOutcome post =
                dispatcher.dispatch(
                        HookPoint.POST_MODEL_CALL,
                        context,
                        (current, binding) ->
                                new PostModelCallContext(
                                        current.snapshot().sessionId(),
                                        binding,
                                        current.modelResponse(),
                                        current.sharedData()),
                        Set.of());
        if (post instanceof LifecycleDispatchOutcome.EndTurn end) {
            return new ModelStepOutcome.EndTurn(end.reason());
        }
        commitAssistant(context);
        return context.modelResponse().toolCalls().isEmpty()
                ? new ModelStepOutcome.FinalText(context.modelResponse().text())
                : new ModelStepOutcome.ToolCalls(context.modelResponse().toolCalls());
    }

    private ModelResponse invokeModel(ApexAgentContext context, String contentId) {
        int maxAttempts = MODEL_RETRY_COUNT + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return context.ports().modelGateway().stream(
                        context.modelRequest(),
                        new ModelStreamObserver() {
                            @Override
                            public void onChunk(ModelStreamChunk chunk) {
                                context.ports().cancellationToken().throwIfCancellationRequested();
                                if (chunk.textDelta() != null && !chunk.textDelta().isEmpty()) {
                                    emitter.publish(
                                            events.streamContent(contentId, chunk.textDelta()));
                                }
                            }

                            @Override
                            public CancellationToken cancellationToken() {
                                return context.ports().cancellationToken();
                            }
                        });
            } catch (CancellationRequestedException error) {
                throw error;
            } catch (RuntimeException error) {
                if (context.ports().cancellationToken().isCancellationRequested()) {
                    throw new CancellationRequestedException();
                }
                LOG.log(
                        System.Logger.Level.WARNING,
                        "模型调用失败: attempt=" + attempt + "/" + maxAttempts,
                        error);
                if (attempt == maxAttempts) {
                    context.recordModelFailure(error);
                    throw new ModelInvocationException(error);
                }
            }
        }
        throw new IllegalStateException("模型重试循环未正常收口");
    }

    /** 将模型文本和 ToolCall 一并写为一条助手消息，以维持会话顺序。 */
    private void commitAssistant(ApexAgentContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (!context.modelResponse().toolCalls().isEmpty()) {
            payload.put(
                    "toolCalls",
                    context.modelResponse().toolCalls().stream().map(this::toolCallMap).toList());
        }
        AgentMessageEntry entry =
                new AgentMessageEntry(
                        context.ports().idGenerator().newEntryId(),
                        context.snapshot().sessionId(),
                        context.snapshot().currentTurnNo(),
                        context.allocateSortNo(),
                        MessageRole.ASSISTANT,
                        context.modelResponse().toolCalls().isEmpty()
                                ? MessageType.TEXT
                                : MessageType.TOOL_CALLS,
                        context.modelResponse().text(),
                        payload,
                        context.ports().timeProvider().now());
        context.appendConversation(List.of(entry));
        context.updateIteration(
                context.modelRequest(),
                context.modelResponse(),
                List.of(),
                IterationStatus.IN_PROGRESS,
                null);
        context.save();
    }

    private Map<String, Object> toolCallMap(ToolCall call) {
        return Map.of(
                "toolCallId",
                call.toolCallId(),
                "name",
                call.name(),
                "ordinal",
                call.ordinal(),
                "arguments",
                call.arguments(),
                "metadata",
                call.metadata());
    }

    public sealed interface ModelStepOutcome {
        record FinalText(String text) implements ModelStepOutcome {}

        record ToolCalls(List<ToolCall> calls) implements ModelStepOutcome {
            public ToolCalls {
                calls = List.copyOf(calls);
            }
        }

        record EndTurn(String reason) implements ModelStepOutcome {}
    }
}
