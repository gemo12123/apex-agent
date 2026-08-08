package org.gemo.apex.runtime.repository.memory;

import java.util.*;
import java.util.concurrent.*;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.extension.repository.ConversationRepository;

public final class InMemoryConversationRepository implements ConversationRepository {
    private final Map<String, State> map = new ConcurrentHashMap<>();

    public void append(List<AgentMessageEntry> es) {
        if (es.isEmpty()) {
            return;
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

    public List<AgentMessageEntry> load(ConversationQuery q) {
        var s = map.get(q.sessionId());
        return s == null ? List.of() : s.items.stream().map(this::copy).toList();
    }

    public void compact(ConversationCompactionCommit c) {
        map.compute(
                c.sessionId(),
                (k, v) -> {
                    var s = v == null ? new State() : v.copy();
                    if (s.compactions.add(c.compactionId())) {
                        s.items.clear();
                        s.items.addAll(c.finalMessages().stream().map(this::copy).toList());
                    }
                    return s;
                });
    }

    private AgentMessageEntry copy(AgentMessageEntry e) {
        return JsonUtils.deepCopy(e, AgentMessageEntry.class);
    }

    private static final class State {
        final List<AgentMessageEntry> items = new ArrayList<>();
        final Set<String> compactions = new HashSet<>();

        State copy() {
            var s = new State();
            s.items.addAll(items);
            s.compactions.addAll(compactions);
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
