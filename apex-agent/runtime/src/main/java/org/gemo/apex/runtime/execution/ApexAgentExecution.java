package org.gemo.apex.runtime.execution;

import org.gemo.apex.core.agent.*;
import org.gemo.apex.runtime.event.OnceAgentEventPublisher;

import java.util.concurrent.atomic.*;

public final class ApexAgentExecution implements AutoCloseable {
    public enum State {PREPARED, RUNNING, CANCEL_REQUESTED, TERMINATED}

    private final ApexAgent agent;
    private final RuntimeCancellationSource cancel;
    private final SessionExecutionLease lease;
    private final OnceAgentEventPublisher events;
    private final ActiveExecutionRegistry registry;
    private final AtomicReference<State> state = new AtomicReference<>(State.PREPARED);

    public ApexAgentExecution(ApexAgent a, RuntimeCancellationSource c, SessionExecutionLease l, OnceAgentEventPublisher e, ActiveExecutionRegistry r) {
        agent = a;
        cancel = c;
        lease = l;
        events = e;
        registry = r;
    }

    public AgentRunOutcome run() {
        if (!state.compareAndSet(State.PREPARED, State.RUNNING))
            throw new IllegalStateException("execution 只能运行一次");
        try {
            return agent.run();
        } finally {
            terminate();
        }
    }

    public void execute() {
        run();
    }

    public boolean cancel() {
        if (state.compareAndSet(State.PREPARED, State.CANCEL_REQUESTED)) {
            cancel.cancel();
            try {
                agent.cancelBeforeRun();
            } finally {
                terminate();
            }
            return true;
        }
        if (state.compareAndSet(State.RUNNING, State.CANCEL_REQUESTED)) return cancel.cancel();
        return false;
    }

    public boolean cancelBeforeStart() {
        return state.get() == State.PREPARED && cancel();
    }

    public void close() {
        cancel();
    }

    public State state() {
        return state.get();
    }

    private void terminate() {
        if (state.getAndSet(State.TERMINATED) == State.TERMINATED) return;
        try {
            events.end();
        } finally {
            try {
                lease.release();
            } finally {
                registry.unregister(this);
            }
        }
    }
}
