package org.gemo.apex.runtime.repository.memory;

import java.util.*;
import java.util.concurrent.*;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.extension.repository.ConversationRepository;

public final class InMemoryConversationRepository implements ConversationRepository {
    private final Map<String, State> map = new ConcurrentHashMap<>();

    public void commit(ConversationWriteBatch batch) {
        map.compute(
                batch.sessionId(),
                (k, v) -> {
                    var s = v == null ? new State() : v.copy();
                    for (ConversationWrite write : batch.writes()) {
                        switch (write) {
                            case AppendConversationWrite append -> s.add(copy(append.entry()));
                            case ReplaceConversationWrite replace -> replace(s, replace);
                            case RemoveConversationWrite remove -> remove(s, remove);
                            case CompactConversationWrite compact -> compact(s, compact.commit());
                        }
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

    private void replace(State state, ReplaceConversationWrite write) {
        int index = indexOfEditable(state, write.targetEntryId());
        AgentMessageEntry old = state.items.get(index);
        state.items.set(
                index,
                new AgentMessageEntry(
                        old.entryId(),
                        old.sessionId(),
                        old.turnNo(),
                        old.sortNo(),
                        write.role(),
                        write.messageType(),
                        write.content(),
                        write.payload(),
                        old.createdTime()));
    }

    private void remove(State state, RemoveConversationWrite write) {
        state.items.remove(indexOfEditable(state, write.targetEntryId()));
    }

    private int indexOfEditable(State state, String entryId) {
        if (state.compactedEntryIds.contains(entryId)) {
            throw new IllegalStateException("已压缩消息不可编辑: " + entryId);
        }
        for (int index = 0; index < state.items.size(); index++) {
            if (state.items.get(index).entryId().equals(entryId)) {
                return index;
            }
        }
        throw new IllegalStateException("消息不存在: " + entryId);
    }

    private void compact(State s, ConversationCompactionCommit c) {
        ConversationCompactionCommit existing = s.compactions.get(c.summary().compactionId());
        if (existing != null && !existing.equals(c)) {
            throw new IllegalStateException("compactionId 内容冲突: " + c.summary().compactionId());
        }
        if (existing == null) {
            validateRetainedMessages(s, c);
            s.compactions.put(c.summary().compactionId(), c);
            s.summary = c.summary();
            s.items.stream()
                    .filter(
                            item ->
                                    item.sortNo() >= c.summary().sourceStartSortNo()
                                            && item.sortNo() <= c.summary().sourceEndSortNo())
                    .map(AgentMessageEntry::entryId)
                    .forEach(s.compactedEntryIds::add);
        }
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
