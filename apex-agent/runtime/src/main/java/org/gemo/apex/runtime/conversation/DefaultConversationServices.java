package org.gemo.apex.runtime.conversation;

import java.util.*;
import java.util.stream.*;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.message.*;
import org.gemo.apex.extension.conversation.*;
import org.gemo.apex.extension.repository.ConversationRepository;

public final class DefaultConversationServices {
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

    public static ConversationCompactor compactor() {
        return r -> {
            int discardedCount = r.sourceMessages().size() - r.retainedMessages().size();
            List<String> parts = new ArrayList<>();
            if (r.previousSummary() != null) {
                parts.add(r.previousSummary().content());
            }
            r.sourceMessages().subList(0, discardedCount).stream()
                    .map(x -> Objects.toString(x.content(), ""))
                    .filter(value -> !value.isEmpty())
                    .forEach(parts::add);
            return new ConversationCompactionResult(
                    r.compactionId(), String.join("\n", parts), r.retainedMessages(), Map.of());
        };
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
