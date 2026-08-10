package org.gemo.apex.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.extension.model.ModelGateway;
import org.gemo.apex.platform.config.ApexAgentPlatformProperties;
import org.gemo.apex.platform.web.sse.RequestBoundAgentEventPublisherFactory;
import org.gemo.apex.platform.web.sse.SseEmitterAgentEventPublisher;
import org.gemo.apex.runtime.api.ApexAgentRuntime;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ChatControllerIntegrationTest {
    private ApexAgentRuntime runtime;

    @AfterEach
    void closeRuntime() {
        if (runtime != null) {
            runtime.close();
        }
    }

    /** 实际Sse应通过Controller和Once输出精确一次End */
    @Test
    void actualSseUsesControllerAndOncePublisherToEmitEndExactlyOnce() throws Exception {
        var fixture = fixture(Runnable::run);
        MvcResult started =
                fixture.mvc()
                        .perform(
                                post("/api/sse/chat")
                                        .header("X-User-Id", "user-1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"sessionId\":\"normal\",\"agentKey\":\"default\",\"type\":\"NEW\",\"query\":\"hello\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String body =
                fixture.mvc()
                        .perform(asyncDispatch(started))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(1, occurrences(body, "\"event_type\":\"END\""));
    }

    /** Agent循环异常依次输出TaskError和裸End */
    @Test
    void emitsTaskErrorBeforeBareEndWhenAgentLoopFails() throws Exception {
        var fixture =
                fixture(
                        Runnable::run,
                        (request, observer) -> {
                            throw new IllegalStateException("model down");
                        });
        MvcResult started =
                fixture.mvc()
                        .perform(
                                post("/api/sse/chat")
                                        .header("X-User-Id", "user-1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"sessionId\":\"failed\",\"agentKey\":\"default\",\"type\":\"NEW\",\"query\":\"hello\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String body =
                fixture.mvc()
                        .perform(asyncDispatch(started))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String taskError =
                "{\"event_type\":\"TASK_ERROR\",\"context\":{\"mode\":\"react\"},\"messages\":[{\"message\":\"model down\"}]}";
        String end = "{\"event_type\":\"END\"}";
        assertEquals(1, occurrences(body, taskError));
        assertEquals(1, occurrences(body, end));
        assertTrue(body.indexOf(taskError) < body.indexOf(end));
    }

    /** 参数错误返回400且Busy返回409并且均无End */
    @Test
    void returns400ForInvalidParametersAnd409ForBusyWithoutEnd() throws Exception {
        var fixture = fixture(Runnable::run);
        fixture.mvc()
                .perform(
                        post("/api/sse/chat")
                                .header("X-User-Id", "user-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sessionId\":\"\",\"agentKey\":\"default\",\"type\":\"NEW\",\"query\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .string(
                                        Matchers.not(
                                                Matchers.containsString(
                                                        "\"event_type\":\"END\""))));

        SseEmitter emitter = new SseEmitter();
        var publisher = new SseEmitterAgentEventPublisher(emitter);
        var first =
                fixture.publishers()
                        .prepare(
                                "busy",
                                "default",
                                "user-1",
                                publisher,
                                () ->
                                        runtime.newAgent(
                                                new AgentRequest(
                                                        "busy", "default", "user-1", "first")));
        fixture.mvc()
                .perform(
                        post("/api/sse/chat")
                                .header("X-User-Id", "user-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sessionId\":\"busy\",\"agentKey\":\"default\",\"type\":\"NEW\",\"query\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(
                        content()
                                .string(
                                        Matchers.not(
                                                Matchers.containsString(
                                                        "\"event_type\":\"END\""))));
        first.cancelBeforeStart();
        publisher.completeFromExecution();
    }

    /** 构造失败和线程池拒绝都只输出End */
    @Test
    void emitsOnlyEndForConstructionFailureAndExecutorRejection() throws Exception {
        var normal = fixture(Runnable::run);
        MvcResult failed =
                normal.mvc()
                        .perform(
                                post("/api/sse/chat")
                                        .header("X-User-Id", "user-1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"sessionId\":\"bad-agent\",\"agentKey\":\"missing\",\"type\":\"NEW\",\"query\":\"x\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String failedBody =
                normal.mvc()
                        .perform(asyncDispatch(failed))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(1, occurrences(failedBody, "\"event_type\":\"END\""));
        assertFalse(failedBody.contains("STREAM_CONTENT"));

        runtime.close();
        runtime = null;
        var rejected =
                fixture(
                        command -> {
                            throw new TaskRejectedException("rejected");
                        });
        MvcResult rejectedResult =
                rejected.mvc()
                        .perform(
                                post("/api/sse/chat")
                                        .header("X-User-Id", "user-1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"sessionId\":\"rejected\",\"agentKey\":\"default\",\"type\":\"NEW\",\"query\":\"x\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String rejectedBody =
                rejected.mvc()
                        .perform(asyncDispatch(rejectedResult))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(1, occurrences(rejectedBody, "\"event_type\":\"END\""));
        assertEquals(0, runtime.activeExecutionCount());
    }

    private Fixture fixture(Executor executor) {
        return fixture(
                executor, (request, observer) -> new ModelResponse("完成", List.of(), Map.of()));
    }

    private Fixture fixture(Executor executor, ModelGateway modelGateway) {
        var publishers = new RequestBoundAgentEventPublisherFactory();
        runtime =
                ApexAgentRuntime.builder()
                        .modelGateway(modelGateway)
                        .defaultEventPublisherFactory(publishers)
                        .build();
        var properties = new ApexAgentPlatformProperties();
        var service = new ChatService(runtime, publishers, executor, properties);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ChatController(service)).build();
        return new Fixture(mvc, publishers);
    }

    private int occurrences(String text, String target) {
        return (text.length() - text.replace(target, "").length()) / target.length();
    }

    private record Fixture(MockMvc mvc, RequestBoundAgentEventPublisherFactory publishers) {}
}
