package org.gemo.apex.runtime.repository.memory;

import java.util.*;
import java.util.concurrent.*;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.snapshot.SessionSnapshot;
import org.gemo.apex.extension.repository.SessionRepository;

public final class InMemorySessionRepository implements SessionRepository {
    private final Map<String, SessionSnapshot> map = new ConcurrentHashMap<>();

    private SessionSnapshot copy(SessionSnapshot s) {
        return JsonUtils.deepCopy(s, SessionSnapshot.class);
    }

    public Optional<SessionSnapshot> load(String id) {
        return Optional.ofNullable(map.get(id)).map(this::copy);
    }

    public void save(SessionSnapshot s) {
        map.put(s.sessionId(), copy(s));
    }
}
