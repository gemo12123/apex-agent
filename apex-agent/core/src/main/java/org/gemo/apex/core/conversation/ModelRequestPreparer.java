package org.gemo.apex.core.conversation;

import java.util.*;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.conversation.ConversationCompactionResult;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.PostMessageCompressionContext;
import org.gemo.apex.common.hook.context.PreMessageCompressionContext;
import org.gemo.apex.common.hook.operation.ToolActivationDelta;
import org.gemo.apex.common.hook.result.ContinuePreMessageCompression;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.tool.ToolDefinition;
import org.gemo.apex.core.agent.ApexAgentContext;
import org.gemo.apex.core.lifecycle.LifecycleDispatchOutcome;
import org.gemo.apex.core.lifecycle.LifecycleDispatcher;
import org.gemo.apex.core.model.ModelRequestSizeEstimator;

public final class ModelRequestPreparer {
    private final LifecycleDispatcher dispatcher;
    private final ModelRequestSizeEstimator sizeEstimator = new ModelRequestSizeEstimator();

    public ModelRequestPreparer(LifecycleDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public PreparationOutcome prepare(ApexAgentContext context, boolean finalIteration) {
        context.ports().cancellationToken().throwIfCancellationRequested();
        var compression = context.definition().definition().messageCompression();
        ModelRequest base = buildModelRequest(context, finalIteration);
        context.modelRequest(base);
        if (!compression.enabled()) {
            return new PreparationOutcome.Prepared(base);
        }
        String compactionId = context.ports().idGenerator().newCompactionId();
        CompressionState initial = compressionState(context, base, compactionId);
        if (initial == null) {
            return new PreparationOutcome.Prepared(base);
        }
        var state = new java.util.concurrent.atomic.AtomicReference<>(initial);
        context.compactionRequest(initial.request());
        LifecycleDispatchOutcome pre =
                dispatcher.dispatch(
                        HookPoint.PRE_MESSAGE_COMPRESSION,
                        context,
                        (current, binding) ->
                                new PreMessageCompressionContext(
                                        current.snapshot().sessionId(),
                                        binding,
                                        current.modelRequest(),
                                        state.get().check(),
                                        current.compactionRequest(),
                                        current.sharedData()),
                        Set.of(),
                        (binding, result) -> {
                            if (!(result instanceof ContinuePreMessageCompression continued)
                                    || (continued.mutations().messageOperations().isEmpty()
                                            && empty(
                                                    continued.mutations().toolActivationDelta()))) {
                                return new LifecycleDispatchOutcome.Continued();
                            }
                            ConversationCompactionRequest patched = context.compactionRequest();
                            ModelRequest rebuilt = buildModelRequest(context, finalIteration);
                            context.modelRequest(rebuilt);
                            CompressionState next =
                                    compressionState(context, rebuilt, compactionId);
                            if (next == null) {
                                state.set(null);
                                return new LifecycleDispatchOutcome.Bypassed();
                            }
                            state.set(next);
                            if (continued.mutations().messageOperations().isEmpty()) {
                                validatePatchedRequest(patched, next);
                                context.compactionRequest(patched);
                            } else {
                                context.compactionRequest(next.request());
                            }
                            return new LifecycleDispatchOutcome.Continued();
                        });
        if (pre instanceof LifecycleDispatchOutcome.EndTurn end) {
            return new PreparationOutcome.EndTurn(end.reason());
        }
        if (pre instanceof LifecycleDispatchOutcome.Bypassed) {
            return new PreparationOutcome.Prepared(context.modelRequest());
        }
        CompressionState effective = state.get();
        validateCompactionRequest(
                context,
                effective.activeMessages(),
                compactionId,
                context.conversationWindow().summary());
        context.ports().cancellationToken().throwIfCancellationRequested();
        context.compactionResult(context.ports().compactor().compact(context.compactionRequest()));
        context.resetPostCompressionOperations();
        LifecycleDispatchOutcome post =
                dispatcher.dispatch(
                        HookPoint.POST_MESSAGE_COMPRESSION,
                        context,
                        (current, binding) ->
                                new PostMessageCompressionContext(
                                        current.snapshot().sessionId(),
                                        binding,
                                        current.compactionRequest().sourceMessages(),
                                        current.compactionResult(),
                                        current.sharedData()),
                        Set.of());
        validateCompactionResult(context.compactionRequest(), context.compactionResult());
        ConversationSummary summary = buildSummary(context, context.compactionResult());
        commit(
                context,
                context.compactionResult(),
                summary,
                context.drainPostCompressionOperations());
        if (post instanceof LifecycleDispatchOutcome.EndTurn end) {
            return new PreparationOutcome.EndTurn(end.reason());
        }
        ModelRequest compacted = buildModelRequest(context, finalIteration);
        context.modelRequest(compacted);
        return new PreparationOutcome.Prepared(compacted);
    }

    private boolean empty(ToolActivationDelta delta) {
        return delta.enable().isEmpty() && delta.disable().isEmpty();
    }

    private ModelRequest buildModelRequest(ApexAgentContext context, boolean finalIteration) {
        List<ToolDefinition> tools =
                context.toolCatalog().ordered().stream()
                        .filter(
                                tool ->
                                        context.snapshot()
                                                .enabledTools()
                                                .contains(tool.definition().name()))
                        .map(tool -> tool.definition())
                        .toList();
        String systemPrompt =
                context.definition().definition().prompt().systemPrompt()
                        + (finalIteration
                                ? "\n" + context.ports().finalIterationInstruction()
                                : "");
        return new ModelRequest(
                systemPrompt,
                context.definition().prefixDeveloperMessages(),
                context.conversationWindow().messages(),
                tools,
                Map.of());
    }

    private CompressionState compressionState(
            ApexAgentContext context, ModelRequest base, String compactionId) {
        var compression = context.definition().definition().messageCompression();
        List<AgentMessageEntry> activeMessages =
                context.conversationWindow().messages().stream()
                        .filter(message -> message.messageType() != MessageType.SUMMARY)
                        .toList();
        if (activeMessages.isEmpty()) {
            return null;
        }
        int actualRetainCount =
                Math.min(compression.maxMessages() / 2, Math.max(0, activeMessages.size() - 1));
        ModelRequestSizeEstimator.Size size = sizeEstimator.estimate(base);
        ConversationCompactionCheck check =
                new ConversationCompactionCheck(
                        activeMessages,
                        size.messageTokens(),
                        size.messageCharacters(),
                        size.systemTokens(),
                        size.systemCharacters(),
                        size.toolTokens(),
                        size.toolCharacters(),
                        size.totalTokens(),
                        size.totalCharacters(),
                        compression.maxMessages(),
                        compression.tokenThreshold(),
                        compression.characterHardLimit(),
                        actualRetainCount,
                        new ConversationCompactionTrigger(
                                context.snapshot().sessionId(),
                                context.snapshot().currentTurnNo(),
                                context.snapshot().activeTurn().currentIteration().iterationNo(),
                                "MODEL_CALL"));
        if (!context.ports().compactionPolicy().shouldCompact(check)) {
            return null;
        }
        List<AgentMessageEntry> retained =
                activeMessages.subList(
                        activeMessages.size() - actualRetainCount, activeMessages.size());
        ConversationCompactionRequest request =
                new ConversationCompactionRequest(
                        context.snapshot().sessionId(),
                        compactionId,
                        activeMessages,
                        retained,
                        context.conversationWindow().summary(),
                        Map.of());
        return new CompressionState(base, activeMessages, check, request);
    }

    private void validatePatchedRequest(
            ConversationCompactionRequest patched, CompressionState state) {
        if (!patched.sessionId().equals(state.request().sessionId())
                || !patched.compactionId().equals(state.request().compactionId())
                || !patched.sourceMessages().equals(state.activeMessages())
                || !Objects.equals(patched.previousSummary(), state.request().previousSummary())) {
            throw new IllegalStateException("工具启停后压缩 Patch 与当前消息窗口不一致");
        }
    }

    private void commit(
            ApexAgentContext context,
            ConversationCompactionResult result,
            ConversationSummary summary,
            List<org.gemo.apex.common.hook.operation.MessageOperation> operations) {
        context.compactConversation(
                new ConversationCompactionCommit(
                        context.snapshot().sessionId(), summary, result.retainedMessages()),
                operations);
        context.save();
    }

    private void validateCompactionRequest(
            ApexAgentContext context,
            List<AgentMessageEntry> activeMessages,
            String compactionId,
            ConversationSummary previousSummary) {
        ConversationCompactionRequest request = context.compactionRequest();
        if (!context.snapshot().sessionId().equals(request.sessionId())
                || !compactionId.equals(request.compactionId())
                || !Objects.equals(previousSummary, request.previousSummary())) {
            throw new IllegalStateException("压缩 Hook 不能改变请求关联信息");
        }
        if (!activeMessages.equals(request.sourceMessages())) {
            throw new IllegalStateException("压缩 Hook 不能改变来源消息");
        }
        validateRetainedTail(
                request.sourceMessages(), request.retainedMessages(), "压缩请求 retainedMessages");
    }

    private void validateCompactionResult(
            ConversationCompactionRequest request, ConversationCompactionResult result) {
        if (!request.compactionId().equals(result.compactionId())) {
            throw new IllegalStateException("压缩结果 compactionId 与请求不一致");
        }
        validateRetainedTail(
                request.sourceMessages(), result.retainedMessages(), "压缩结果 retainedMessages");
    }

    private void validateRetainedTail(
            List<AgentMessageEntry> source, List<AgentMessageEntry> retained, String fieldName) {
        if (retained.size() >= source.size()) {
            throw new IllegalStateException(fieldName + " 必须至少淘汰一条消息");
        }
        List<AgentMessageEntry> expected =
                source.subList(source.size() - retained.size(), source.size());
        if (!expected.equals(retained)) {
            throw new IllegalStateException(fieldName + " 必须是未经修改的连续尾部");
        }
    }

    private ConversationSummary buildSummary(
            ApexAgentContext context, ConversationCompactionResult result) {
        Set<String> retainedIds =
                result.retainedMessages().stream()
                        .map(AgentMessageEntry::entryId)
                        .collect(java.util.stream.Collectors.toSet());
        List<AgentMessageEntry> discarded =
                context.compactionRequest().sourceMessages().stream()
                        .filter(message -> !retainedIds.contains(message.entryId()))
                        .toList();
        if (discarded.isEmpty()) {
            throw new IllegalStateException("压缩结果必须至少淘汰一条消息");
        }
        ConversationSummary previous = context.compactionRequest().previousSummary();
        return new ConversationSummary(
                result.compactionId(),
                result.summary(),
                previous == null ? discarded.getFirst().sortNo() : previous.sourceStartSortNo(),
                discarded.getLast().sortNo(),
                discarded.getLast().turnNo(),
                context.ports().timeProvider().now());
    }

    public sealed interface PreparationOutcome {
        record Prepared(ModelRequest request) implements PreparationOutcome {}

        record EndTurn(String reason) implements PreparationOutcome {}
    }

    private record CompressionState(
            ModelRequest base,
            List<AgentMessageEntry> activeMessages,
            ConversationCompactionCheck check,
            ConversationCompactionRequest request) {}
}
