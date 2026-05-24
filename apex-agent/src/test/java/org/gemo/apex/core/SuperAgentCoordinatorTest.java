package org.gemo.apex.core;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.domain.dto.ChatRequest;
import org.gemo.apex.message.EndMessage;
import org.gemo.apex.util.JacksonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuperAgentCoordinatorTest {

    @Mock
    private SuperAgentFactory factory;

    private SuperAgentCoordinator coordinator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        coordinator = new SuperAgentCoordinator(factory, Runnable::run);
    }

    @Test
    void runShouldRejectConcurrentRequestBeforeFactoryCreate() {
        ChatRequest request = new ChatRequest();
        request.setSessionId("session-1");
        request.setAgentKey("agent-1");
        request.setQuery("hello");

        SuperAgent runningAgent = mock(SuperAgent.class);
        ReflectionTestUtils.setField(coordinator, "runningAgents",
                new ConcurrentHashMap<>(Map.of("session-1", runningAgent)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> coordinator.run(request, new SseEmitter(1000L)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(factory, never()).create(any(), any());
    }

    @Test
    void runShouldSendEmptyEndAndCleanupWhenExecutionFinishes() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setSessionId("session-1");
        request.setAgentKey("agent-1");
        request.setQuery("hello");

        RecordingSseEmitter emitter = new RecordingSseEmitter();
        SuperAgent agent = mock(SuperAgent.class);
        SuperAgentContext context = new SuperAgentContext();
        context.setSessionId("session-1");
        context.setSseEmitter(emitter);
        when(agent.getContext()).thenReturn(context);
        when(factory.create(eq(request), same(emitter))).thenReturn(agent);

        coordinator.run(request, emitter);

        assertTrue(emitter.awaitCompletion());
        assertTrue(emitter.joinedPayload().contains(JacksonUtils.toJson(EndMessage.builder().build())));
        assertTrue(currentRunningAgents().isEmpty());
        verify(agent).run();
    }

    @Test
    void runShouldSendEmptyEndAndCleanupWhenTaskSubmissionIsRejected() throws Exception {
        coordinator = new SuperAgentCoordinator(factory, command -> {
            throw new TaskRejectedException("queue full");
        });

        ChatRequest request = new ChatRequest();
        request.setSessionId("session-1");
        request.setAgentKey("agent-1");
        request.setQuery("hello");

        RecordingSseEmitter emitter = new RecordingSseEmitter();
        SuperAgent agent = mock(SuperAgent.class);
        SuperAgentContext context = new SuperAgentContext();
        context.setSessionId("session-1");
        context.setSseEmitter(emitter);
        when(agent.getContext()).thenReturn(context);
        when(factory.create(eq(request), same(emitter))).thenReturn(agent);

        coordinator.run(request, emitter);

        assertTrue(emitter.awaitCompletion());
        assertTrue(emitter.joinedPayload().contains(JacksonUtils.toJson(EndMessage.builder().build())));
        assertTrue(currentRunningAgents().isEmpty());
    }

    @Test
    void runShouldStillSendEmptyEndWhenAgentThrows() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setSessionId("session-1");
        request.setAgentKey("agent-1");
        request.setQuery("hello");

        RecordingSseEmitter emitter = new RecordingSseEmitter();
        SuperAgent agent = mock(SuperAgent.class);
        SuperAgentContext context = new SuperAgentContext();
        context.setSessionId("session-1");
        context.setSseEmitter(emitter);
        when(agent.getContext()).thenReturn(context);
        when(factory.create(eq(request), same(emitter))).thenReturn(agent);
        doThrow(new IllegalStateException("boom")).when(agent).run();

        coordinator.run(request, emitter);

        assertTrue(emitter.awaitCompletion());
        assertTrue(emitter.joinedPayload().contains(JacksonUtils.toJson(EndMessage.builder().build())));
        assertTrue(currentRunningAgents().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private Map<String, SuperAgent> currentRunningAgents() {
        return (Map<String, SuperAgent>) ReflectionTestUtils.getField(coordinator, "runningAgents");
    }

    private static class RecordingSseEmitter extends SseEmitter {
        private final StringBuilder payloads = new StringBuilder();
        private final CountDownLatch completionLatch = new CountDownLatch(1);

        RecordingSseEmitter() {
            super(1000L);
        }

        @Override
        public synchronized void send(Object object) throws IOException {
            payloads.append(object);
        }

        @Override
        public synchronized void complete() {
            completionLatch.countDown();
        }

        boolean awaitCompletion() throws InterruptedException {
            return completionLatch.await(2, TimeUnit.SECONDS);
        }

        String joinedPayload() {
            return payloads.toString();
        }
    }
}
