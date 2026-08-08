package org.gemo.apex.platform.web.sse;

import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.runtime.api.ApexAgentRuntime;
import org.gemo.apex.runtime.execution.ApexAgentExecution;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SseEmitterCancellationTest {
    /**
     * callback早于Execution绑定时应通过二次检查取消并释放Lease
     */
    @Test
    void cancelsAndReleasesLeaseThroughSecondCheckWhenCallbackPrecedesExecutionBinding() {
        var factory = new RequestBoundAgentEventPublisherFactory();
        try (var runtime = ApexAgentRuntime.builder().modelGateway((request, observer) ->
                        new ModelResponse("完成", List.of(), Map.of()))
                .defaultEventPublisherFactory(factory).build()) {
            SseEmitter emitter = new SseEmitter();
            var publisher = new SseEmitterAgentEventPublisher(emitter);
            ApexAgentExecution execution = factory.prepare("race", "default", "user-1", publisher,
                    () -> runtime.newAgent(new AgentRequest("race", "default", "user-1", "hello")));

            publisher.transportClosed();
            publisher.bind(execution);

            assertEquals(ApexAgentExecution.State.TERMINATED, execution.state());
            assertEquals(0, runtime.activeExecutionCount());
            assertFalse(execution.cancel());
        }
    }
}
