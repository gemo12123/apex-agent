package org.gemo.apex.extension.repository;

import java.util.Optional;
import org.gemo.apex.common.snapshot.SessionSnapshot;

public interface SessionRepository {
    Optional<SessionSnapshot> load(String sessionId);

    void save(SessionSnapshot snapshot);
}
