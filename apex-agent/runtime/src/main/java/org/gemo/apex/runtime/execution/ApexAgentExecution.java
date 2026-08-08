package org.gemo.apex.runtime.execution;

import org.gemo.apex.core.agent.*;
import org.gemo.apex.runtime.event.OnceAgentEventPublisher;

import java.util.concurrent.atomic.*;

/**
 * 连接 core Agent 与 runtime 资源管理的单次执行句柄。
 *
 * <p>无论正常结束、执行失败还是取消，{@link #terminate()} 都会恰好一次释放会话 lease、活动登记和
 * 请求级事件发布器。</p>
 */
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

    /** 将状态从 PREPARED 推进到 RUNNING 后执行 Agent；取消可在任一时刻由 cancellation source 感知。 */
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

    /** 仅发出协作式取消信号；不等待不合作的模型或工具返回。 */
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

    /** 幂等释放执行持有的所有运行时资源。 */
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
