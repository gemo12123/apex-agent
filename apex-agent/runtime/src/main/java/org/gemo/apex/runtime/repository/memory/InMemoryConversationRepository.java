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
                        s.items.stream()
                                .filter(item -> !s.compactedEntryIds.contains(item.entryId()))
                                .map(this::copy)
                                .toList());
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
                        validateRetainedMessages(s, c);
                        s.compactions.put(c.summary().compactionId(), c);
                        s.summary = c.summary();
                        s.items.stream()
                                .filter(
                                        item ->
                                                item.sortNo()
                                                                >= c.summary()
                                                                        .sourceStartSortNo()
                                                        && item.sortNo()
                                                                <= c.summary()
                                                                        .sourceEndSortNo())
                                .map(AgentMessageEntry::entryId)
                                .forEach(s.compactedEntryIds::add);
                    }
                    return s;
                });
    }

    private void validateRetainedMessages(State state, ConversationCompactionCommit commit) {
        Map<String, AgentMessageEntry> existing =
                state.items.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AgentMessageEntry::entryId, item -> item));
        for (AgentMessageEntry retained : commit.retainedMessages()) {
            AgentMessageEntry stored = existing.get(retained.entryId());
            if (stored == null || !samePersistentMessage(stored, retained)) {
                throw new IllegalStateException("保留消息不存在或内容冲突: " + retained.entryId());
            }
            if (state.compactedEntryIds.contains(retained.entryId())) {
                throw new IllegalStateException("保留消息已经被压缩: " + retained.entryId());
            }
        }
    }

    private boolean samePersistentMessage(AgentMessageEntry left, AgentMessageEntry right) {
        return left.entryId().equals(right.entryId())
                && left.sessionId().equals(right.sessionId())
                && left.turnNo() == right.turnNo()
                && left.sortNo() == right.sortNo()
                && left.role() == right.role()
                && left.messageType() == right.messageType()
                && Objects.equals(left.content(), right.content())
                && left.payload().equals(right.payload());
    }

    private AgentMessageEntry copy(AgentMessageEntry e) {
        return JsonUtils.deepCopy(e, AgentMessageEntry.class);
    }

    private static final class State {
        final List<AgentMessageEntry> items = new ArrayList<>();
        final Map<String, ConversationCompactionCommit> compactions = new HashMap<>();
        final Set<String> compactedEntryIds = new HashSet<>();
        ConversationSummary summary;

        State copy() {
            var s = new State();
            s.items.addAll(items);
            s.compactions.putAll(compactions);
            s.compactedEntryIds.addAll(compactedEntryIds);
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
