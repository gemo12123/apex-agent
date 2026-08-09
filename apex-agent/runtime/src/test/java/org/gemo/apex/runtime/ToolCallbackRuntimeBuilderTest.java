package org.gemo.apex.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.tool.*;
import org.gemo.apex.core.agent.AgentRunOutcome;
import org.gemo.apex.extension.tool.AgentTool;
import org.gemo.apex.extension.tool.ToolExecutionObserver;
import org.gemo.apex.runtime.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.*;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

class ToolCallbackRuntimeBuilderTest {
    @Test
    void snapshotsProviderOnceAndExecutesItsCallback() {
        AtomicInteger providerReads = new AtomicInteger();
        AtomicInteger callbackCalls = new AtomicInteger();
        AtomicInteger modelCalls = new AtomicInteger();
        ToolCallback callback = callback("remote_search", callbackCalls, false);
        ToolCallbackProvider provider =
                () -> {
                    providerReads.incrementAndGet();
                    return new ToolCallback[] {callback};
                };

        try (var runtime =
                ApexAgentRuntime.builder()
                        .modelGateway(
                                (request, observer) ->
                                        modelCalls.getAndIncrement() == 0
                                                ? new ModelResponse(
                                                        "",
                                                        List.of(
                                                                new ToolCall(
                                                                        "call-1",
                                                                        "remote_search",
                                                                        0,
                                                                        Map.of("query", "apex"),
                                                                        Map.of(
                                                                                "springAiType",
                                                                                "function"))),
                                                        Map.of())
                                                : new ModelResponse("完成", List.of(), Map.of()))
                        .agentDefinition(definition("remote_search"))
                        .toolCallingManager(ToolCallingManager.builder().build())
                        .registerToolCallbackProvider(provider)
                        .defaultEventPublisherFactory(descriptor -> event -> {})
                        .build()) {
            assertEquals(1, providerReads.get());
            assertInstanceOf(
                    AgentRunOutcome.Completed.class,
                    runtime.newAgent(new AgentRequest("session", "default", "user", "搜索")).run());
            assertAll(
                    () -> assertEquals(1, providerReads.get()),
                    () -> assertEquals(1, callbackCalls.get()),
                    () -> assertEquals(2, modelCalls.get()));
        }
    }

    @Test
    void rejectsMissingManagerReturnDirectAndDuplicateNames() {
        ToolCallback normal = callback("search", new AtomicInteger(), false);
        ToolCallback direct = callback("direct", new AtomicInteger(), true);

        assertAll(
                () ->
                        assertThrows(
                                RuntimeConfigurationException.class,
                                () ->
                                        ApexAgentRuntime.builder()
                                                .modelGateway(
                                                        (request, observer) ->
                                                                new ModelResponse(
                                                                        "完成", List.of(), Map.of()))
                                                .registerToolCallback(normal)
                                                .build()),
                () ->
                        assertThrows(
                                RuntimeConfigurationException.class,
                                () ->
                                        ApexAgentRuntime.builder()
                                                .modelGateway(
                                                        (request, observer) ->
                                                                new ModelResponse(
                                                                        "完成", List.of(), Map.of()))
                                                .toolCallingManager(
                                                        ToolCallingManager.builder().build())
                                                .registerToolCallback(direct)
                                                .build()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        ApexAgentRuntime.builder()
                                                .modelGateway(
                                                        (request, observer) ->
                                                                new ModelResponse(
                                                                        "完成", List.of(), Map.of()))
                                                .toolCallingManager(
                                                        ToolCallingManager.builder().build())
                                                .registerTool(nativeTool("search"))
                                                .registerToolCallback(normal)
                                                .build()));
    }

    private static AgentDefinition definition(String toolName) {
        return new AgentDefinition(
                "1.0.0",
                new AgentMetadata("default", "默认", "测试"),
                new PromptDefinition("系统", 3),
                new MessageCompressionDefinition(false, 10),
                new ToolSetDefinition(Set.of(toolName), Set.of(toolName)),
                Set.of(),
                Map.of(),
                Map.of());
    }

    private static ToolCallback callback(String name, AtomicInteger calls, boolean returnDirect) {
        return new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return new DefaultToolDefinition(name, "测试工具", "{\"type\":\"object\"}");
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().returnDirect(returnDirect).build();
            }

            @Override
            public String call(String input) {
                calls.incrementAndGet();
                return "结果";
            }
        };
    }

    private static AgentTool nativeTool(String name) {
        return new AgentTool() {
            @Override
            public ToolDefinition definition() {
                return new ToolDefinition(name, "本地工具", "{}", Map.of());
            }

            @Override
            public ToolResult execute(
                    ToolCall call, ToolExecutionContext context, ToolExecutionObserver observer) {
                return new ToolResult(call.toolCallId(), call.name(), "结果", Map.of());
            }
        };
    }
}
