package org.gemo.apex.extension.repository;

import org.gemo.apex.common.snapshot.SessionSnapshot;

import java.util.Optional;

public interface SessionRepository {
    Optional<SessionSnapshot> load(String sessionId);

    void save(SessionSnapshot snapshot);
}
