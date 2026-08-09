package org.gemo.apex.runtime.model.springai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.gemo.apex.common.exception.CancellationRequestedException;
import org.gemo.apex.common.tool.*;
import org.gemo.apex.extension.tool.ToolExecutionObserver;
import org.gemo.apex.protocol.event.AgentMessage;
import org.gemo.apex.runtime.execution.RuntimeCancellationSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.*;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

class SpringAiToolCallbackAgentToolTest {
    @Test
    void mapsDefinitionAndExecutesOneApprovedToolCallThroughManager() {
        ToolCallback callback = callback("mcp_weather", false);
        CapturingManager manager =
                new CapturingManager(
                        executionResult(
                                false,
                                new ToolResponseMessage.ToolResponse(
                                        "call-1", "mcp_weather", "晴天")));
        var adapter = new SpringAiToolCallbackAgentTool(callback, manager);
        var source = new RuntimeCancellationSource();
        ToolCall call =
                new ToolCall(
                        "call-1",
                        "mcp_weather",
                        0,
                        Map.of("city", "上海"),
                        Map.of("springAiType", "custom"));

        ToolResult result = adapter.execute(call, context(source), observer(source));

        assertAll(
                () -> assertEquals("mcp_weather", adapter.definition().name()),
                () -> assertEquals("天气查询", adapter.definition().description()),
                () -> assertEquals("{\"type\":\"object\"}", adapter.definition().inputSchemaJson()),
                () -> assertEquals("晴天", result.content()),
                () -> assertEquals("call-1", result.toolCallId()),
                () -> assertEquals("mcp_weather", result.toolName()));
        ToolCallingChatOptions options =
                assertInstanceOf(ToolCallingChatOptions.class, manager.prompt.get().getOptions());
        assertAll(
                () -> assertEquals(List.of(callback), options.getToolCallbacks()),
                () -> assertEquals(Boolean.FALSE, options.getInternalToolExecutionEnabled()));
        AssistantMessage.ToolCall springCall =
                manager.response.get().getResult().getOutput().getToolCalls().getFirst();
        assertAll(
                () -> assertEquals("call-1", springCall.id()),
                () -> assertEquals("custom", springCall.type()),
                () -> assertEquals("mcp_weather", springCall.name()),
                () -> assertEquals("{\"city\":\"上海\"}", springCall.arguments()));
    }

    @Test
    void rejectsInvalidManagerResultsAndReturnDirect() {
        ToolCallback callback = callback("search", false);
        ToolCall call = new ToolCall("call-1", "search", 0, Map.of(), Map.of());
        var source = new RuntimeCancellationSource();

        assertAll(
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        execute(
                                                callback,
                                                resultWithHistory(false, List.of()),
                                                call,
                                                source)),
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        execute(
                                                callback,
                                                executionResult(
                                                        false,
                                                        new ToolResponseMessage.ToolResponse(
                                                                "call-1", "search", "a"),
                                                        new ToolResponseMessage.ToolResponse(
                                                                "call-1", "search", "b")),
                                                call,
                                                source)),
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        execute(
                                                callback,
                                                executionResult(
                                                        false,
                                                        new ToolResponseMessage.ToolResponse(
                                                                "other", "search", "a")),
                                                call,
                                                source)),
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        execute(
                                                callback,
                                                executionResult(
                                                        true,
                                                        new ToolResponseMessage.ToolResponse(
                                                                "call-1", "search", "a")),
                                                call,
                                                source)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new SpringAiToolCallbackAgentTool(
                                                callback("direct", true),
                                                new CapturingManager(executionResult(false)))));
    }

    @Test
    void interruptsManagerOnCancellationAndClearsItsInterruptFlag() throws Exception {
        ToolCallback callback = callback("slow", false);
        RuntimeCancellationSource source = new RuntimeCancellationSource();
        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interruptedAfterExecute = new AtomicBoolean(true);
        ToolCallingManager manager =
                new ToolCallingManager() {
                    @Override
                    public List<org.springframework.ai.tool.definition.ToolDefinition>
                            resolveToolDefinitions(ToolCallingChatOptions options) {
                        return List.of(callback.getToolDefinition());
                    }

                    @Override
                    public ToolExecutionResult executeToolCalls(
                            Prompt prompt, ChatResponse chatResponse) {
                        entered.countDown();
                        try {
                            new CountDownLatch(1).await();
                            throw new AssertionError("取消后不应继续执行");
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Manager 已中断", error);
                        }
                    }
                };
        Thread worker =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try {
                                        new SpringAiToolCallbackAgentTool(callback, manager)
                                                .execute(
                                                        new ToolCall(
                                                                "call-1", "slow", 0, Map.of(),
                                                                Map.of()),
                                                        context(source),
                                                        observer(source));
                                    } catch (Throwable error) {
                                        failure.set(error);
                                        interruptedAfterExecute.set(
                                                Thread.currentThread().isInterrupted());
                                    }
                                });

        assertTrue(entered.await(2, TimeUnit.SECONDS));
        source.cancel();
        worker.join(2_000);

        assertAll(
                () -> assertFalse(worker.isAlive()),
                () -> assertInstanceOf(CancellationRequestedException.class, failure.get()),
                () -> assertFalse(interruptedAfterExecute.get()));
    }

    private static ToolResult execute(
            ToolCallback callback,
            ToolExecutionResult executionResult,
            ToolCall call,
            RuntimeCancellationSource source) {
        return new SpringAiToolCallbackAgentTool(callback, new CapturingManager(executionResult))
                .execute(call, context(source), observer(source));
    }

    private static ToolExecutionResult executionResult(
            boolean returnDirect, ToolResponseMessage.ToolResponse... responses) {
        return resultWithHistory(
                returnDirect,
                List.of(ToolResponseMessage.builder().responses(List.of(responses)).build()));
    }

    private static ToolExecutionResult resultWithHistory(
            boolean returnDirect, List<Message> history) {
        return ToolExecutionResult.builder()
                .conversationHistory(history)
                .returnDirect(returnDirect)
                .build();
    }

    private static ToolCallback callback(String name, boolean returnDirect) {
        return new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return new DefaultToolDefinition(name, "天气查询", "{\"type\":\"object\"}");
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().returnDirect(returnDirect).build();
            }

            @Override
            public String call(String input) {
                throw new AssertionError("测试由 Manager 返回固定结果");
            }
        };
    }

    private static ToolExecutionContext context(RuntimeCancellationSource source) {
        return new ToolExecutionContext(
                "session", 1, 1, "user", null, null, source.token(), Map.of());
    }

    private static ToolExecutionObserver observer(RuntimeCancellationSource source) {
        return new ToolExecutionObserver() {
            @Override
            public void onEvent(AgentMessage event) {}

            @Override
            public CancellationToken cancellationToken() {
                return source.token();
            }
        };
    }

    private static final class CapturingManager implements ToolCallingManager {
        private final ToolExecutionResult result;
        private final AtomicReference<Prompt> prompt = new AtomicReference<>();
        private final AtomicReference<ChatResponse> response = new AtomicReference<>();

        private CapturingManager(ToolExecutionResult result) {
            this.result = result;
        }

        @Override
        public List<org.springframework.ai.tool.definition.ToolDefinition> resolveToolDefinitions(
                ToolCallingChatOptions options) {
            return options.getToolCallbacks().stream()
                    .map(ToolCallback::getToolDefinition)
                    .toList();
        }

        @Override
        public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
            this.prompt.set(prompt);
            response.set(chatResponse);
            return result;
        }
    }
}
