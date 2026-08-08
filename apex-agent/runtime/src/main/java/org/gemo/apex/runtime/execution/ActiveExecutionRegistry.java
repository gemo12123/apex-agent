package org.gemo.apex.runtime.execution;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class ActiveExecutionRegistry {
    private final Set<ApexAgentExecution> active = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean accepting = new AtomicBoolean(true), closed = new AtomicBoolean();
    private volatile Runnable onEmpty = () -> {
    };

    public void ensureAccepting() {
        if (!accepting.get()) throw new IllegalStateException("runtime 已关闭");
    }

    public void register(ApexAgentExecution e) {
        ensureAccepting();
        active.add(e);
        if (!accepting.get() && active.remove(e)) throw new IllegalStateException("runtime 已关闭");
    }

    void unregister(ApexAgentExecution e) {
        active.remove(e);
        closeIfEmpty();
    }

    public int size() {
        return active.size();
    }

    public List<ApexAgentExecution> closeGate(Runnable action) {
        onEmpty = action;
        accepting.set(false);
        var snapshot = List.copyOf(active);
        closeIfEmpty();
        return snapshot;
    }

    private void closeIfEmpty() {
        if (!accepting.get() && active.isEmpty() && closed.compareAndSet(false, true)) onEmpty.run();
    }
}
