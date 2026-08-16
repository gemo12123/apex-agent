package org.gemo.apex.core.conversation;

import java.util.*;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.conversation.ConversationCompactionResult;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.PostMessageCompressionContext;
import org.gemo.apex.common.hook.context.PreMessageCompressionContext;
import org.gemo.apex.common.hook.operation.AppendConversationMessage;
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
        int maxMessages = compression.maxMessages();
        ConversationWindow window = context.conversationWindow();
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
        ModelRequest base =
                new ModelRequest(
                        systemPrompt,
                        context.definition().prefixDeveloperMessages(),
                        window.messages(),
                        tools,
                        Map.of());
        context.modelRequest(base);
        if (!compression.enabled()) {
            return new PreparationOutcome.Prepared(base);
        }
        List<AgentMessageEntry> activeMessages =
                window.messages().stream()
                        .filter(message -> message.messageType() != MessageType.SUMMARY)
                        .toList();
        if (activeMessages.isEmpty()) {
            return new PreparationOutcome.Prepared(base);
        }
        int actualRetainCount = Math.min(maxMessages / 2, Math.max(0, activeMessages.size() - 1));
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
                        maxMessages,
                        compression.tokenThreshold(),
                        compression.characterHardLimit(),
                        actualRetainCount,
                        new ConversationCompactionTrigger(
                                context.snapshot().sessionId(),
                                context.snapshot().currentTurnNo(),
                                context.snapshot().activeTurn().currentIteration().iterationNo(),
                                "MODEL_CALL"));
        if (!context.ports().compactionPolicy().shouldCompact(check)) {
            return new PreparationOutcome.Prepared(base);
        }
        String compactionId = context.ports().idGenerator().newCompactionId();
        List<AgentMessageEntry> retained =
                activeMessages.subList(
                        activeMessages.size() - actualRetainCount, activeMessages.size());
        context.compactionRequest(
                new ConversationCompactionRequest(
                        context.snapshot().sessionId(),
                        compactionId,
                        activeMessages,
                        retained,
                        window.summary(),
                        Map.of()));
        LifecycleDispatchOutcome pre =
                dispatcher.dispatch(
                        HookPoint.PRE_MESSAGE_COMPRESSION,
                        context,
                        (current, binding) ->
                                new PreMessageCompressionContext(
                                        current.snapshot().sessionId(),
                                        binding,
                                        current.modelRequest(),
                                        check,
                                        current.compactionRequest(),
                                        current.sharedData()),
                        Set.of());
        if (pre instanceof LifecycleDispatchOutcome.EndTurn end) {
            return new PreparationOutcome.EndTurn(end.reason());
        }
        validateCompactionRequest(context, activeMessages, compactionId, window.summary());
        context.ports().cancellationToken().throwIfCancellationRequested();
        context.compactionResult(context.ports().compactor().compact(context.compactionRequest()));
        context.resetPostCompressionAppends();
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
                context.drainPostCompressionAppends());
        if (post instanceof LifecycleDispatchOutcome.EndTurn end) {
            return new PreparationOutcome.EndTurn(end.reason());
        }
        ModelRequest compacted =
                new ModelRequest(
                        systemPrompt,
                        base.prefixDeveloperMessages(),
                        context.conversationWindow().messages(),
                        tools,
                        base.options());
        context.modelRequest(compacted);
        return new PreparationOutcome.Prepared(compacted);
    }

    private void commit(
            ApexAgentContext context,
            ConversationCompactionResult result,
            ConversationSummary summary,
            List<AppendConversationMessage> appends) {
        context.compactConversation(
                new ConversationCompactionCommit(
                        context.snapshot().sessionId(), summary, result.retainedMessages()));
        appendPostCompressionMessages(context, appends);
        context.save();
    }

    private void appendPostCompressionMessages(
            ApexAgentContext context, List<AppendConversationMessage> appends) {
        if (appends.isEmpty()) {
            return;
        }
        List<AgentMessageEntry> entries = new ArrayList<>();
        for (AppendConversationMessage append : appends) {
            entries.add(
                    new AgentMessageEntry(
                            context.ports().idGenerator().newEntryId(),
                            context.snapshot().sessionId(),
                            context.snapshot().currentTurnNo(),
                            context.allocateSortNo(),
                            append.role(),
                            append.messageType(),
                            append.content(),
                            append.payload(),
                            context.ports().timeProvider().now()));
        }
        context.appendConversation(entries);
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
            List<AgentMessageEntry> source,
            List<AgentMessageEntry> retained,
            String fieldName) {
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
}
