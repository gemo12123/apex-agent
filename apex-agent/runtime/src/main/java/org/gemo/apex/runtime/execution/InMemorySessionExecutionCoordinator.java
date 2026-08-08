package org.gemo.apex.runtime.execution;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class InMemorySessionExecutionCoordinator implements SessionExecutionCoordinator {
    private final Map<String, String> owners = new ConcurrentHashMap<>();

    public SessionExecutionLease acquire(String id) {
        String owner = UUID.randomUUID().toString();
        if (owners.putIfAbsent(id, owner) != null) {
            throw new SessionBusyException(id);
        }
        return new SessionExecutionLease() {
            final AtomicBoolean done = new AtomicBoolean();

            public String sessionId() {
                return id;
            }

            public void release() {
                if (done.compareAndSet(false, true)) {
                    owners.remove(id, owner);
                }
            }
        };
    }
}
