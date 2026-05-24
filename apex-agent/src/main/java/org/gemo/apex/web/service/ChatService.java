package org.gemo.apex.web.service;

import org.gemo.apex.core.SuperAgentCoordinator;
import org.gemo.apex.domain.dto.ChatRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {

    private final SuperAgentCoordinator coordinator;

    public ChatService(SuperAgentCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    public SseEmitter chat(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(600000L);
        coordinator.run(request, emitter);
        return emitter;
    }
}
