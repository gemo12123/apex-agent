package org.gemo.apex.runtime.repository.memory;

import java.util.*;
import java.util.concurrent.*;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.extension.repository.ConversationRepository;

public final class InMemoryConversationRepository implements ConversationRepository {
    private final Map<String, State> map = new ConcurrentHashMap<>();

    public void append(List<AgentMessageEntry> es) {
        if (es.isEmpty()) {
            return;
        }
        if (es.stream().anyMatch(entry -> entry.messageType() == MessageType.SUMMARY)) {
            throw new IllegalArgumentException("SUMMARY 消息不能写入对话消息仓储");
        }
        map.compute(
                es.getFirst().sessionId(),
                (k, v) -> {
                    var s = v == null ? new State() : v.copy();
                    for (var e : es) {
                        s.add(copy(e));
                    }
                    return s;
                });
    }

    public ConversationHistory load(ConversationQuery q) {
        var s = map.get(q.sessionId());
        return s == null
                ? new ConversationHistory(q.sessionId(), Optional.empty(), List.of())
                : new ConversationHistory(
                        q.sessionId(),
                        Optional.ofNullable(s.summary),
                        s.items.stream().map(this::copy).toList());
    }

    public void compact(ConversationCompactionCommit c) {
        map.compute(
                c.sessionId(),
                (k, v) -> {
                    var s = v == null ? new State() : v.copy();
                    ConversationCompactionCommit existing =
                            s.compactions.get(c.summary().compactionId());
                    if (existing != null && !existing.equals(c)) {
                        throw new IllegalStateException(
                                "compactionId 内容冲突: " + c.summary().compactionId());
                    }
                    if (existing == null) {
                        s.compactions.put(c.summary().compactionId(), c);
                        s.summary = c.summary();
                        c.finalMessages().stream().map(this::copy).forEach(s::add);
                    }
                    return s;
                });
    }

    private AgentMessageEntry copy(AgentMessageEntry e) {
        return JsonUtils.deepCopy(e, AgentMessageEntry.class);
    }

    private static final class State {
        final List<AgentMessageEntry> items = new ArrayList<>();
        final Map<String, ConversationCompactionCommit> compactions = new HashMap<>();
        ConversationSummary summary;

        State copy() {
            var s = new State();
            s.items.addAll(items);
            s.compactions.putAll(compactions);
            s.summary = summary;
            return s;
        }

        void add(AgentMessageEntry e) {
            for (var x : items) {
                if (x.entryId().equals(e.entryId())) {
                    if (!x.equals(e)) {
                        throw new IllegalStateException("entryId 冲突");
                    }
                    return;
                }
                if (x.sortNo() == e.sortNo()) {
                    throw new IllegalStateException("sortNo 冲突");
                }
            }
            items.add(e);
            items.sort(Comparator.comparingLong(AgentMessageEntry::sortNo));
        }
    }
}
