package org.gemo.apex.runtime.api;

import org.gemo.apex.common.execution.*;
import org.gemo.apex.common.intervention.HumanResponseCommand;
import org.gemo.apex.core.agent.*;
import org.gemo.apex.extension.event.*;
import org.gemo.apex.runtime.event.*;
import org.gemo.apex.runtime.execution.*;
import org.gemo.apex.runtime.resource.RuntimeResources;

import java.util.*;

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

    public ApexAgentExecution newAgent(AgentRequest r) {
        return prepare(r.sessionId(), r.agentKey(), r.userId(), RequestKind.NEW, p -> factory.createNew(r, p));
    }

    public ApexAgentExecution resumeAgent(HumanResponseCommand r) {
        return prepare(r.sessionId(), r.agentKey(), r.userId(), RequestKind.HUMAN_RESPONSE, p -> factory.createResumed(r, p));
    }

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

    public void close() {
        active.closeGate(resources::close).forEach(ApexAgentExecution::cancel);
    }
}
