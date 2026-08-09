package org.gemo.apex.runtime.conversation;

import java.util.*;
import java.util.stream.*;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.message.*;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.tool.CancellationToken;
import org.gemo.apex.extension.conversation.*;
import org.gemo.apex.extension.model.ModelGateway;
import org.gemo.apex.extension.model.ModelStreamObserver;
import org.gemo.apex.extension.repository.ConversationRepository;

public final class DefaultConversationServices {
    private static final String COMPACTION_SYSTEM_PROMPT =
            """
            你负责把历史对话压缩为可供后续模型继续工作的累计摘要。
            请忠实保留用户目标、约束、已确认事实、关键决策、未完成事项、必要标识符和重要工具结果。
            合并已有累计摘要与本次待压缩消息，不要臆测，不要遗漏会影响后续执行的信息。
            输入不包含仍以原文保留的尾部消息。只输出摘要正文，不要输出标题、解释或代码块。
            """
                    .strip();

    private DefaultConversationServices() {}

    public static ConversationWindowManager window(ConversationRepository r) {
        return q -> {
            ConversationHistory history = r.load(q.query());
            List<AgentMessageEntry> messages = new ArrayList<>();
            history.messages().stream()
                    .filter(message -> !coveredBy(history.summary(), message.sortNo()))
                    .forEach(messages::add);
            history.summary()
                    .map(summary -> summaryMessage(history.sessionId(), summary))
                    .ifPresent(messages::add);
            messages.sort(Comparator.comparingLong(AgentMessageEntry::sortNo));
            return new ConversationWindow(
                    q.query().sessionId(),
                    history.summary().orElse(null),
                    messages,
                    messages.isEmpty() ? null : messages.getFirst().sortNo(),
                    messages.isEmpty() ? null : messages.getLast().sortNo());
        };
    }

    public static ConversationCompactionPolicy policy() {
        return c ->
                c.messages().size() > c.messageThreshold()
                        || (c.tokenThreshold() != null
                                && c.totalTokenEstimate() >= c.tokenThreshold())
                        || (c.characterHardLimit() != null
                                && c.totalCharacterEstimate() >= c.characterHardLimit());
    }

    public static ConversationCompactor compactor(
            ModelGateway modelGateway, CancellationToken cancellationToken) {
        Objects.requireNonNull(modelGateway, "modelGateway");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        return r -> {
            int discardedCount = r.sourceMessages().size() - r.retainedMessages().size();
            if (discardedCount <= 0) {
                throw new IllegalArgumentException("压缩请求必须至少包含一条待压缩消息");
            }
            cancellationToken.throwIfCancellationRequested();
            AgentMessageEntry input = compactionInput(r, discardedCount);
            var response =
                    modelGateway.stream(
                            new ModelRequest(
                                    COMPACTION_SYSTEM_PROMPT, List.of(input), List.of(), Map.of()),
                            new ModelStreamObserver() {
                                @Override
                                public void onChunk(
                                        org.gemo.apex.common.model.ModelStreamChunk chunk) {}

                                @Override
                                public CancellationToken cancellationToken() {
                                    return cancellationToken;
                                }
                            });
            cancellationToken.throwIfCancellationRequested();
            if (!response.toolCalls().isEmpty()) {
                throw new IllegalStateException("消息压缩模型必须返回纯文本摘要");
            }
            if (response.text() == null || response.text().isBlank()) {
                throw new IllegalStateException("消息压缩模型返回了空摘要");
            }
            return new ConversationCompactionResult(
                    r.compactionId(), response.text().strip(), r.retainedMessages(), Map.of());
        };
    }

    private static AgentMessageEntry compactionInput(
            ConversationCompactionRequest request, int discardedCount) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put(
                "previousSummary",
                request.previousSummary() == null ? "" : request.previousSummary().content());
        input.put(
                "messages",
                request.sourceMessages().subList(0, discardedCount).stream()
                        .map(DefaultConversationServices::messageInput)
                        .toList());
        return new AgentMessageEntry(
                "compaction-input:" + request.compactionId(),
                request.sessionId(),
                0,
                0,
                MessageRole.USER,
                MessageType.TEXT,
                JsonUtils.toJson(input),
                Map.of(),
                java.time.Instant.EPOCH);
    }

    private static Map<String, Object> messageInput(AgentMessageEntry message) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("turnNo", message.turnNo());
        value.put("sortNo", message.sortNo());
        value.put("role", message.role().name());
        value.put("messageType", message.messageType().name());
        value.put("content", Objects.toString(message.content(), ""));
        value.put("payload", message.payload());
        return value;
    }

    private static boolean coveredBy(Optional<ConversationSummary> summary, long sortNo) {
        return summary.isPresent()
                && sortNo >= summary.get().sourceStartSortNo()
                && sortNo <= summary.get().sourceEndSortNo();
    }

    private static AgentMessageEntry summaryMessage(String sessionId, ConversationSummary summary) {
        return new AgentMessageEntry(
                "summary:" + summary.compactionId(),
                sessionId,
                summary.sourceTurnNo(),
                summary.sourceEndSortNo(),
                MessageRole.SYSTEM,
                MessageType.SUMMARY,
                summary.content(),
                Map.of(
                        "sourceStartSortNo", summary.sourceStartSortNo(),
                        "sourceEndSortNo", summary.sourceEndSortNo()),
                summary.updatedTime());
    }
}
