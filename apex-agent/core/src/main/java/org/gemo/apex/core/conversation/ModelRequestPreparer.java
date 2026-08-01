package org.gemo.apex.core.conversation;

import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.PostMessageCompressionContext;
import org.gemo.apex.common.hook.context.PreMessageCompressionContext;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.tool.ToolDefinition;
import org.gemo.apex.core.agent.ApexAgentContext;
import org.gemo.apex.core.lifecycle.LifecycleDispatchOutcome;
import org.gemo.apex.core.lifecycle.LifecycleDispatcher;

import java.util.*;

public final class ModelRequestPreparer {
    private final LifecycleDispatcher dispatcher;

    public ModelRequestPreparer(LifecycleDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public PreparationOutcome prepare(ApexAgentContext context, boolean finalIteration) {
        context.ports().cancellationToken().throwIfCancellationRequested();
        int maxMessages = context.definition().definition().messageCompression().maxMessages();
        int retainCount = Math.min(maxMessages / 2, maxMessages);
        ConversationWindow window = context.ports().windowManager().prepare(new ConversationWindowRequest(
                new ConversationQuery(context.snapshot().sessionId()), maxMessages, retainCount));
        List<ToolDefinition> tools = context.toolCatalog().ordered().stream()
                .filter(tool -> context.snapshot().enabledTools().contains(tool.definition().name()))
                .map(tool -> tool.definition()).toList();
        String systemPrompt = context.definition().definition().prompt().systemPrompt()
                + (finalIteration ? "\n" + context.ports().finalIterationInstruction() : "");
        ModelRequest base = new ModelRequest(systemPrompt, window.messages(), tools, Map.of());
        context.modelRequest(base);
        long messageChars = window.messages().stream().mapToLong(message ->
                (message.content() == null ? 0 : message.content().length()) + message.payload().toString().length()).sum();
        long systemChars = systemPrompt.length();
        long toolChars = tools.stream().mapToLong(tool -> tool.description().length()
                + tool.inputSchemaJson().length()).sum();
        int actualRetainCount = Math.min(retainCount, window.messages().size());
        long messageTokens = estimateTokens(messageChars);
        long systemTokens = estimateTokens(systemChars);
        long toolTokens = estimateTokens(toolChars);
        ConversationCompactionCheck check = new ConversationCompactionCheck(window.messages(),
                messageTokens, messageChars, systemTokens, systemChars,
                toolTokens, toolChars, messageTokens + systemTokens + toolTokens,
                messageChars + systemChars + toolChars, context.ports().modelRequestHardLimit(),
                context.ports().modelRequestHardLimit() * 4, actualRetainCount,
                new ConversationCompactionTrigger(context.snapshot().sessionId(),
                        context.snapshot().currentTurnNo(),
                        context.snapshot().activeTurn().currentIteration().iterationNo(), "MODEL_CALL"));
        if (!context.ports().compactionPolicy().shouldCompact(check)) {
            return new PreparationOutcome.Prepared(base);
        }
        if (window.messages().isEmpty()) {
            throw new IllegalStateException("压缩策略不能对空窗口返回 true");
        }
        String compactionId = context.ports().idGenerator().newCompactionId();
        List<AgentMessageEntry> retained = window.messages().subList(
                Math.max(0, window.messages().size() - actualRetainCount), window.messages().size());
        context.compactionRequest(new ConversationCompactionRequest(context.snapshot().sessionId(),
                compactionId, window.messages(), retained, Map.of()));
        LifecycleDispatchOutcome pre = dispatcher.dispatch(HookPoint.PRE_MESSAGE_COMPRESSION, context,
                current -> new PreMessageCompressionContext(current.snapshot().sessionId(),
                        current.modelRequest(), check, current.compactionRequest()), Set.of());
        if (pre instanceof LifecycleDispatchOutcome.EndTurn end) {
            return new PreparationOutcome.EndTurn(end.reason());
        }
        context.ports().cancellationToken().throwIfCancellationRequested();
        context.compactionResult(context.ports().compactor().compact(context.compactionRequest()));
        LifecycleDispatchOutcome post = dispatcher.dispatch(HookPoint.POST_MESSAGE_COMPRESSION, context,
                current -> new PostMessageCompressionContext(current.snapshot().sessionId(),
                        window.messages(), current.compactionResult()), Set.of());
        commit(context, window, context.compactionResult());
        if (post instanceof LifecycleDispatchOutcome.EndTurn end) {
            return new PreparationOutcome.EndTurn(end.reason());
        }
        ModelRequest compacted = new ModelRequest(systemPrompt,
                context.compactionResult().retainedMessages(), tools, base.options());
        context.modelRequest(compacted);
        return new PreparationOutcome.Prepared(compacted);
    }

    private void commit(ApexAgentContext context, ConversationWindow window,
                        org.gemo.apex.common.conversation.ConversationCompactionResult result) {
        List<String> ids = result.retainedMessages().stream().map(AgentMessageEntry::entryId).toList();
        context.ports().conversationRepository().compact(new ConversationCompactionCommit(
                context.snapshot().sessionId(), result.compactionId(), window.firstSortNo(), window.lastSortNo(),
                result.summary(), ids, result.retainedMessages()));
        context.save();
    }

    private long estimateTokens(long characters) { return (characters + 3) / 4; }

    public sealed interface PreparationOutcome {
        record Prepared(ModelRequest request) implements PreparationOutcome {}
        record EndTurn(String reason) implements PreparationOutcome {}
    }
}
