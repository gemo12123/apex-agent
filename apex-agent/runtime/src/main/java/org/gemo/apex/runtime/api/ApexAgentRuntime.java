package org.gemo.apex.runtime.api;

import org.gemo.apex.common.execution.*;
import org.gemo.apex.common.intervention.HumanResponseCommand;
import org.gemo.apex.core.agent.*;
import org.gemo.apex.extension.event.*;
import org.gemo.apex.runtime.event.*;
import org.gemo.apex.runtime.execution.*;
import org.gemo.apex.runtime.resource.RuntimeResources;

import java.util.*;

/**
 * 运行时门面，负责将外部请求包装为可异步执行的 Agent execution。
 *
 * <p>同一 session 的 NEW/HUMAN_RESPONSE 在创建 execution 前同步取得 lease；资源关闭则先阻止
 * 新 execution，再向活动 execution 发出取消请求。</p>
 */
public final class ApexAgentRuntime implements AutoCloseable {
    interface Ports {
        AgentPorts create(AgentEventPublisher p, RuntimeCancellationSource c);
    }

    private final Ports ports;
    private final SessionExecutionCoordinator coordinator;
    private final AgentEventPublisherFactory publishers;
    private final ActiveExecutionRegistry active;
    private final RuntimeResources resources;
    private final ApexAgentFactory factory = new ApexAgentFactory();

    ApexAgentRuntime(Ports p, SessionExecutionCoordinator c, AgentEventPublisherFactory f, ActiveExecutionRegistry a, RuntimeResources r) {
        ports = p;
        coordinator = c;
        publishers = f;
        active = a;
        resources = r;
    }

    public static ApexAgentRuntimeBuilder builder() {
        return new ApexAgentRuntimeBuilder();
    }

    /** 为新 Turn 准备 execution，并争用该会话的本进程租约。 */
    public ApexAgentExecution newAgent(AgentRequest r) {
        return prepare(r.sessionId(), r.agentKey(), r.userId(), RequestKind.NEW, p -> factory.createNew(r, p));
    }

    /** 为人工响应准备恢复 execution，恢复请求与新请求共享同一会话租约。 */
    public ApexAgentExecution resumeAgent(HumanResponseCommand r) {
        return prepare(r.sessionId(), r.agentKey(), r.userId(), RequestKind.HUMAN_RESPONSE, p -> factory.createResumed(r, p));
    }

    /**
     * 创建请求独立的发布器与取消源；失败时释放 lease，并确保请求级 END 至多发送一次。
     */
    private ApexAgentExecution prepare(String sid, String key, String uid, RequestKind kind, java.util.function.Function<AgentPorts, ApexAgent> create) {
        active.ensureAccepting();
        var lease = coordinator.acquire(sid);
        var cancel = new RuntimeCancellationSource();
        AgentEventPublisher raw;
        try {
            raw = publishers.create(new AgentExecutionDescriptor(UUID.randomUUID().toString(), sid, key, uid, kind));
        } catch (RuntimeException e) {
            lease.release();
            throw e;
        }
        var once = new OnceAgentEventPublisher(raw, cancel);
        try {
            var ex = new ApexAgentExecution(create.apply(ports.create(once, cancel)), cancel, lease, once, active);
            active.register(ex);
            return ex;
        } catch (IllegalStateException closed) {
            try {
                once.end();
            } finally {
                lease.release();
            }
            throw closed;
        } catch (RuntimeException e) {
            try {
                once.end();
            } finally {
                lease.release();
            }
            throw new AgentPreparationException(e, true);
        }
    }

    public int activeExecutionCount() {
        return active.size();
    }

    /** 关闭入口并取消所有活动 execution；实际资源延迟到最后一个 execution 结束后释放。 */
    public void close() {
        active.closeGate(resources::close).forEach(ApexAgentExecution::cancel);
    }
}
