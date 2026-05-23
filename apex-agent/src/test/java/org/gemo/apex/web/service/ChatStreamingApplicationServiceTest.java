package org.gemo.apex.web.service;

import org.gemo.apex.config.ChatExecutionConfiguration;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.core.SuperAgentExecutor;
import org.gemo.apex.core.SessionExecutionGuard;
import org.gemo.apex.core.SuperAgentSessionService;
import org.gemo.apex.domain.dto.ChatRequest;
import org.gemo.apex.message.EndMessage;
import org.gemo.apex.memory.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatStreamingApplicationServiceTest {

    @Mock
    private SuperAgentSessionService sessionService;

    @Mock
    private SuperAgentExecutor executor;

    @Mock
    private ChatTerminalEventFactory terminalEventFactory;

    @InjectMocks
    private ChatStreamingApplicationService service;

    private SessionExecutionGuard sessionExecutionGuard;
    private ThreadPoolTaskExecutor chatStreamExecutor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sessionExecutionGuard = new SessionExecutionGuard();
        ChatExecutionConfiguration configuration = new ChatExecutionConfiguration();
        chatStreamExecutor = configuration.chatStreamExecutor();
        ReflectionTestUtils.setField(service, "sessionExecutionGuard", sessionExecutionGuard);
        ReflectionTestUtils.setField(service, "terminalEventFactory", new ChatTerminalEventFactory());
        ReflectionTestUtils.setField(service, "chatStreamExecutor", chatStreamExecutor);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        if (chatStreamExecutor != null) {
            chatStreamExecutor.shutdown();
        }
    }

    @Test
    void streamShouldEmitCompletedEndAndReleaseGuard() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setSessionId("session-1");
        request.setAgentKey("agent-1");
        request.setQuery("hello");

        RecordingSseEmitter emitter = new RecordingSseEmitter();
        SuperAgentContext context = new SuperAgentContext();
        context.setSessionId("session-1");
        context.setExecutionMode(ModeEnum.REACT);
        context.setExecutionStatus(ExecutionStatus.COMPLETED);
        when(sessionService.createContext("session-1", "agent-1", "hello")).thenReturn(context);

        sessionExecutionGuard.tryAcquire("session-1");
        service.stream(request, emitter);

        assertTrue(emitter.awaitCompletion());
        assertTrue(emitter.joinedPayload().contains("\"execution_status\":\"COMPLETED\""));
        assertTrue(sessionExecutionGuard.tryAcquire("session-1"));
    }

    @Test
    void streamShouldEmitFailedEndWhenExecutionThrows() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setSessionId("session-1");
        request.setAgentKey("agent-1");
        request.setQuery("hello");

        RecordingSseEmitter emitter = new RecordingSseEmitter();
        SuperAgentContext context = new SuperAgentContext();
        context.setSessionId("session-1");
        context.setExecutionMode(ModeEnum.REACT);
        context.setExecutionStatus(ExecutionStatus.FAILED);
        when(sessionService.createContext("session-1", "agent-1", "hello")).thenReturn(context);
        doThrow(new IllegalStateException("boom")).when(executor).execute(context);

        sessionExecutionGuard.tryAcquire("session-1");
        service.stream(request, emitter);

        assertTrue(emitter.awaitCompletion());
        assertTrue(emitter.joinedPayload().contains("\"execution_status\":\"FAILED\""));
        assertTrue(emitter.joinedPayload().contains("\"error_code\":\"STREAM_EXECUTION_FAILED\""));
        assertTrue(sessionExecutionGuard.tryAcquire("session-1"));
    }

    @Test
    void streamShouldEmitFailedEndWhenContextCreationThrowsBeforeContextExists() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setSessionId("session-1");
        request.setAgentKey("agent-1");
        request.setQuery("hello");

        RecordingSseEmitter emitter = new RecordingSseEmitter();
        when(sessionService.createContext("session-1", "agent-1", "hello"))
                .thenThrow(new IllegalStateException("boom"));

        sessionExecutionGuard.tryAcquire("session-1");
        service.stream(request, emitter);

        assertTrue(emitter.awaitCompletion());
        assertTrue(emitter.joinedPayload().contains("\"execution_status\":\"FAILED\""));
        assertTrue(emitter.joinedPayload().contains("\"error_code\":\"STREAM_CONTEXT_INIT_FAILED\""));
        assertTrue(sessionExecutionGuard.tryAcquire("session-1"));
    }

    @Test
    void streamShouldPropagateUserContextAndClearItInWorker() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setSessionId("session-1");
        request.setAgentKey("agent-1");
        request.setQuery("hello");

        RecordingSseEmitter emitter = new RecordingSseEmitter();
        SuperAgentContext context = new SuperAgentContext();
        context.setSessionId("session-1");
        context.setExecutionMode(ModeEnum.REACT);
        context.setExecutionStatus(ExecutionStatus.COMPLETED);
        when(sessionService.createContext("session-1", "agent-1", "hello")).thenReturn(context);

        CountDownLatch executeLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertEquals("user-1", UserContextHolder.getUserId());
            executeLatch.countDown();
            return null;
        }).when(executor).execute(context);

        UserContextHolder.setUserId("user-1");
        sessionExecutionGuard.tryAcquire("session-1");
        service.stream(request, emitter);

        assertTrue(executeLatch.await(2, TimeUnit.SECONDS));
        assertTrue(emitter.awaitCompletion());
        verify(executor).execute(context);
    }

    @Test
    void streamShouldBestEffortCompleteWhenTerminalSendFails() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setSessionId("session-1");
        request.setAgentKey("agent-1");
        request.setQuery("hello");

        FailingSseEmitter emitter = new FailingSseEmitter();
        SuperAgentContext context = new SuperAgentContext();
        context.setSessionId("session-1");
        context.setExecutionMode(ModeEnum.REACT);
        context.setExecutionStatus(ExecutionStatus.COMPLETED);
        when(sessionService.createContext("session-1", "agent-1", "hello")).thenReturn(context);

        sessionExecutionGuard.tryAcquire("session-1");
        service.stream(request, emitter);

        assertTrue(emitter.awaitCompletion());
        assertTrue(sessionExecutionGuard.tryAcquire("session-1"));
    }

    private static class RecordingSseEmitter extends SseEmitter {
        private final List<String> payloads = new ArrayList<>();
        private final CountDownLatch completionLatch = new CountDownLatch(1);

        RecordingSseEmitter() {
            super(1000L);
        }

        @Override
        public synchronized void send(Object object) throws IOException {
            payloads.add(String.valueOf(object));
        }

        @Override
        public synchronized void complete() {
            completionLatch.countDown();
        }

        boolean awaitCompletion() throws InterruptedException {
            return completionLatch.await(2, TimeUnit.SECONDS);
        }

        String joinedPayload() {
            return String.join("\n", payloads);
        }
    }

    private static class FailingSseEmitter extends RecordingSseEmitter {
        @Override
        public synchronized void send(Object object) throws IOException {
            throw new IOException("connection closed");
        }
    }
}
