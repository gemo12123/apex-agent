package org.gemo.apex.core;

import org.gemo.apex.domain.dto.ChatRequest;
import org.gemo.apex.message.EndMessage;
import org.gemo.apex.util.JacksonUtils;
import org.gemo.apex.util.MessageUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SuperAgentCoordinator {

    private final SuperAgentFactory factory;
    private final TaskExecutor chatStreamExecutor;
    private final Map<String, SuperAgent> runningAgents = new ConcurrentHashMap<>();
    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();

    public SuperAgentCoordinator(SuperAgentFactory factory, @Qualifier("chatStreamExecutor") TaskExecutor chatStreamExecutor) {
        this.factory = factory;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    public void run(ChatRequest request, SseEmitter emitter) {
        SuperAgent agent = createAndRegisterAgent(request, emitter);
        if (agent == null) {
            return;
        }
        executeAsync(request.getSessionId(), agent);
    }

    private Object getSessionLock(String sessionId) {
        return sessionLocks.computeIfAbsent(sessionId, key -> new Object());
    }

    private SuperAgent createAndRegisterAgent(ChatRequest request, SseEmitter emitter) {
        String sessionId = request.getSessionId();
        Object sessionLock = getSessionLock(sessionId);
        synchronized (sessionLock) {
            if (runningAgents.containsKey(sessionId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Session " + sessionId + " already has an active execution");
            }
            try {
                SuperAgent agent = factory.create(request, emitter);
                runningAgents.put(sessionId, agent);
                return agent;
            } catch (RuntimeException ex) {
                sendEnd(emitter);
                completeEmitter(emitter);
                return null;
            }
        }
    }

    private void executeAsync(String sessionId, SuperAgent agent) {
        try {
            chatStreamExecutor.execute(() -> doRun(sessionId, agent));
        } catch (TaskRejectedException ex) {
            SseEmitter emitter = agent.getContext().getSseEmitter();
            sendEnd(emitter);
            cleanup(sessionId, agent, emitter);
        }
    }

    private void doRun(String sessionId, SuperAgent agent) {
        SseEmitter emitter = agent.getContext().getSseEmitter();
        try {
            agent.run();
        } catch (RuntimeException ignored) {
            // 终态统一发空 END。
        } finally {
            sendEnd(emitter);
            cleanup(sessionId, agent, emitter);
        }
    }

    private void sendEnd(SseEmitter emitter) {
        MessageUtils.sendMessage(emitter, JacksonUtils.toJson(EndMessage.builder().build()));
    }

    private void cleanup(String sessionId, SuperAgent agent, SseEmitter emitter) {
        try {
            completeEmitter(emitter);
        } finally {
            runningAgents.remove(sessionId, agent);
        }
    }

    private void completeEmitter(SseEmitter emitter) {
        emitter.complete();
    }
}
