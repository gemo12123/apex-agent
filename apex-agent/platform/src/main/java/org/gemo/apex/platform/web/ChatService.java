package org.gemo.apex.platform.web;

import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.intervention.HumanResponseCommand;
import org.gemo.apex.platform.config.ApexAgentPlatformProperties;
import org.gemo.apex.platform.web.sse.RequestBoundAgentEventPublisherFactory;
import org.gemo.apex.platform.web.sse.SseEmitterAgentEventPublisher;
import org.gemo.apex.protocol.request.ChatRequest;
import org.gemo.apex.protocol.request.RequestType;
import org.gemo.apex.runtime.api.AgentPreparationException;
import org.gemo.apex.runtime.api.ApexAgentRuntime;
import org.gemo.apex.runtime.execution.ApexAgentExecution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;
import java.util.Map;

@Service
public class ChatService {
    private final ApexAgentRuntime runtime;
    private final RequestBoundAgentEventPublisherFactory publishers;
    private final Executor executor;
    private final long timeout;

    public ChatService(ApexAgentRuntime runtime, RequestBoundAgentEventPublisherFactory publishers,
                       @Qualifier("agentExecutionExecutor") Executor executor,
                       ApexAgentPlatformProperties properties) {
        this.runtime = runtime;
        this.publishers = publishers;
        this.executor = executor;
        this.timeout = properties.getSseTimeoutMillis();
    }

    public SseEmitter chat(ChatRequest request, String userId) {
        SseEmitter emitter = new SseEmitter(timeout);
        SseEmitterAgentEventPublisher publisher = new SseEmitterAgentEventPublisher(emitter);
        ApexAgentExecution execution;
        try {
            execution = publishers.prepare(request.getSessionId(), request.getAgentKey(), userId, publisher,
                    () -> prepare(request, userId));
        } catch (AgentPreparationException exception) {
            if (!exception.endPublished()) throw exception;
            publisher.completeFromExecution();
            return emitter;
        }
        publisher.bind(execution);
        if (publisher.isClosed()) return emitter;
        try {
            executor.execute(() -> {
                try {
                    execution.execute();
                } finally {
                    publisher.completeFromExecution();
                }
            });
        } catch (TaskRejectedException exception) {
            execution.cancelBeforeStart();
            publisher.completeFromExecution();
        }
        return emitter;
    }

    private ApexAgentExecution prepare(ChatRequest request, String userId) {
        if (request.getType() == RequestType.HUMAN_RESPONSE) {
            return runtime.resumeAgent(new HumanResponseCommand(request.getSessionId(), request.getAgentKey(),
                    userId, normalizedResponse(request.getHumanResponse())));
        }
        return runtime.newAgent(new AgentRequest(request.getSessionId(), request.getAgentKey(), userId,
                request.getQuery()));
    }

    private Map<String, Object> normalizedResponse(Map<String, Object> response) {
        if (response.containsKey("interaction_type")) return response;
        if (response.size() == 1 && response.values().iterator().next() instanceof Map<?, ?> nested) {
            java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
            nested.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return response;
    }
}
