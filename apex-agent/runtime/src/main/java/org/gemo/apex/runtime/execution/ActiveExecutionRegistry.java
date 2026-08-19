package org.gemo.apex.runtime.execution;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * 记录 runtime 拥有的活动 execution，并协调延迟资源关闭。
 *
 * <p>关闭闸门建立后不再接纳新任务；只有活动集合清空时才执行资源关闭回调。
 */
public final class ActiveExecutionRegistry {
    private final Set<ApexAgentExecution> active = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean accepting = new AtomicBoolean(true), closed = new AtomicBoolean();
    private volatile Runnable onEmpty = () -> {};

    public void ensureAccepting() {
        if (!accepting.get()) {
            throw new IllegalStateException("runtime 已关闭");
        }
    }

    /** 注册 execution，并处理 register 与 closeGate 并发时的竞态。 */
    public void register(ApexAgentExecution e) {
        ensureAccepting();
        active.add(e);
        if (!accepting.get() && active.remove(e)) {
            throw new IllegalStateException("runtime 已关闭");
        }
    }

    void unregister(ApexAgentExecution e) {
        active.remove(e);
        closeIfEmpty();
    }

    public int size() {
        return active.size();
    }

    /** 关闭接纳闸门，返回关闭时刻的活动 execution 快照供调用方逐一取消。 */
    public List<ApexAgentExecution> closeGate(Runnable action) {
        onEmpty = action;
        accepting.set(false);
        var snapshot = List.copyOf(active);
        closeIfEmpty();
        return snapshot;
    }

    private void closeIfEmpty() {
        if (!accepting.get() && active.isEmpty() && closed.compareAndSet(false, true)) {
            onEmpty.run();
        }
    }
}
