package org.gemo.apex.web.service;

import org.gemo.apex.core.SuperAgentCoordinator;
import org.gemo.apex.domain.dto.ChatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

class ChatServiceTest {

    @Mock
    private SuperAgentCoordinator coordinator;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        chatService = new ChatService(coordinator);
    }

    @Test
    void chatShouldCreateEmitterAndDelegateToCoordinator() {
        ChatRequest request = new ChatRequest();
        request.setSessionId("session-1");

        SseEmitter emitter = chatService.chat(request);

        assertNotNull(emitter);
        verify(coordinator).run(eq(request), same(emitter));
    }
}
