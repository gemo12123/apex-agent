package org.gemo.apex.platform.web.sse;

import java.util.function.Supplier;
import org.gemo.apex.common.execution.AgentExecutionDescriptor;
import org.gemo.apex.extension.event.AgentEventPublisher;
import org.gemo.apex.extension.event.AgentEventPublisherFactory;

public final class RequestBoundAgentEventPublisherFactory implements AgentEventPublisherFactory {
    private final ThreadLocal<Binding> current = new ThreadLocal<>();

    public <T> T prepare(
            String sessionId,
            String agentKey,
            String userId,
            SseEmitterAgentEventPublisher publisher,
            Supplier<T> action) {
        if (current.get() != null) {
            throw new IllegalStateException("请求 Publisher 已绑定");
        }
        current.set(new Binding(sessionId, agentKey, userId, publisher));
        try {
            return action.get();
        } finally {
            current.remove();
        }
    }

    @Override
    public AgentEventPublisher create(AgentExecutionDescriptor descriptor) {
        Binding binding = current.get();
        if (binding == null || !binding.matches(descriptor)) {
            throw new IllegalStateException("runtime 准备阶段缺少请求 Publisher");
        }
        return binding.publisher();
    }

    private record Binding(
            String sessionId,
            String agentKey,
            String userId,
            SseEmitterAgentEventPublisher publisher) {
        boolean matches(AgentExecutionDescriptor value) {
            return sessionId.equals(value.sessionId())
                    && agentKey.equals(value.agentKey())
                    && userId.equals(value.userId());
        }
    }
}
