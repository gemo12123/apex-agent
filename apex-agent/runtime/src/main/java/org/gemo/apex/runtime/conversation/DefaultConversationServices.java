package org.gemo.apex.runtime.conversation;

import org.gemo.apex.common.conversation.*;
import org.gemo.apex.extension.conversation.*;
import org.gemo.apex.extension.repository.ConversationRepository;

import java.util.*;
import java.util.stream.*;

public final class DefaultConversationServices {
    private DefaultConversationServices() {
    }

    public static ConversationWindowManager window(ConversationRepository r) {
        return q -> {
            var all = r.load(q.query());
            var m = all.subList(Math.max(0, all.size() - q.maxMessages()), all.size());
            return new ConversationWindow(q.query().sessionId(), m, m.isEmpty() ? null : m.getFirst().sortNo(), m.isEmpty() ? null : m.getLast().sortNo());
        };
    }

    public static ConversationCompactionPolicy policy() {
        return c -> c.totalTokenEstimate() >= c.tokenThreshold() || c.totalCharacterEstimate() >= c.characterThreshold();
    }

    public static ConversationCompactor compactor() {
        return r -> new ConversationCompactionResult(r.compactionId(), r.sourceMessages().stream().map(x -> Objects.toString(x.content(), "")).collect(Collectors.joining("\n")), r.retainedMessages(), Map.of());
    }
}
