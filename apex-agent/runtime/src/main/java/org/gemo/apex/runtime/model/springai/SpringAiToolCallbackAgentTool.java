package org.gemo.apex.runtime.model.springai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.gemo.apex.common.exception.CancellationRequestedException;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.tool.*;
import org.gemo.apex.extension.tool.AgentTool;
import org.gemo.apex.extension.tool.ToolExecutionObserver;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.*;
import org.springframework.ai.tool.ToolCallback;

/** 将单个 Spring AI ToolCallback 适配为 Apex 工具，编排生命周期仍由 core 负责。 */
public final class SpringAiToolCallbackAgentTool implements AgentTool {
    private final ToolCallback callback;
    private final ToolCallingManager manager;
    private final ToolDefinition definition;
    private final boolean mcpTool;

    public SpringAiToolCallbackAgentTool(ToolCallback callback, ToolCallingManager manager) {
        this.callback = Objects.requireNonNull(callback, "callback");
        this.manager = Objects.requireNonNull(manager, "manager");
        if (callback.getToolMetadata().returnDirect()) {
            throw new IllegalArgumentException("暂不支持 returnDirect=true 的 ToolCallback");
        }
        var source = Objects.requireNonNull(callback.getToolDefinition(), "callback definition");
        this.definition =
                new ToolDefinition(
                        source.name(), source.description(), source.inputSchema(), Map.of());
        this.mcpTool = isMcpTool(callback);
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(
            ToolCall call, ToolExecutionContext context, ToolExecutionObserver observer) {
        CancellationToken cancellation = context.cancellationToken();
        cancellation.throwIfCancellationRequested();
        Thread executionThread = Thread.currentThread();
        boolean interruptedBefore = executionThread.isInterrupted();
        AtomicBoolean interruptedByCancellation = new AtomicBoolean();
        try (var registration =
                cancellation.onCancel(
                        () -> {
                            interruptedByCancellation.set(true);
                            executionThread.interrupt();
                        })) {
            ToolExecutionResult executionResult;
            try {
                executionResult = manager.executeToolCalls(prompt(), response(call));
            } catch (RuntimeException error) {
                if (cancellation.isCancellationRequested()) {
                    throw new CancellationRequestedException();
                }
                throw error;
            }
            cancellation.throwIfCancellationRequested();
            ToolResult result = mapResult(call, executionResult);
            cancellation.throwIfCancellationRequested();
            return result;
        } finally {
            if (!interruptedBefore && interruptedByCancellation.get()) {
                Thread.interrupted();
            }
        }
    }

    private Prompt prompt() {
        ToolCallingChatOptions options =
                ToolCallingChatOptions.builder()
                        .toolCallbacks(callback)
                        .internalToolExecutionEnabled(false)
                        .build();
        return new Prompt(List.of(), options);
    }

    private ChatResponse response(ToolCall call) {
        String type = Objects.toString(call.metadata().get("springAiType"), "function");
        if (type.isBlank()) {
            type = "function";
        }
        AssistantMessage message =
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(
                                List.of(
                                        new AssistantMessage.ToolCall(
                                                call.toolCallId(),
                                                type,
                                                call.name(),
                                                JsonUtils.toJson(call.arguments()))))
                        .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private ToolResult mapResult(ToolCall call, ToolExecutionResult executionResult) {
        if (executionResult == null) {
            throw new IllegalStateException("ToolCallingManager 未返回执行结果");
        }
        if (executionResult.returnDirect()) {
            throw new IllegalStateException("暂不支持 ToolCallingManager 返回 returnDirect=true");
        }
        List<Message> history = executionResult.conversationHistory();
        if (history == null
                || history.isEmpty()
                || !(history.getLast() instanceof ToolResponseMessage responseMessage)) {
            throw new IllegalStateException("ToolCallingManager 未返回 ToolResponseMessage");
        }
        List<ToolResponseMessage.ToolResponse> responses = responseMessage.getResponses();
        if (responses.size() != 1) {
            throw new IllegalStateException("ToolCallingManager 必须返回且只能返回一条工具响应");
        }
        ToolResponseMessage.ToolResponse response = responses.getFirst();
        if (!call.toolCallId().equals(response.id()) || !call.name().equals(response.name())) {
            throw new IllegalStateException("ToolCallingManager 响应与 ToolCall ID/name 不一致");
        }
        return new ToolResult(response.id(), response.name(), content(response), Map.of());
    }

    private String content(ToolResponseMessage.ToolResponse response) {
        String data = response.responseData();
        return mcpTool ? unwrapMcpContent(data) : data;
    }

    private static boolean isMcpTool(ToolCallback callback) {
        return callback.getClass().getName().contains("McpToolCallback");
    }

    static String unwrapMcpContent(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        try {
            JsonNode root = JsonUtils.parseTree(content);
            if (root == null || !root.isArray() || root.size() != 1) {
                return content;
            }
            JsonNode text = root.get(0).get("text");
            return (text != null && text.isTextual()) ? text.textValue() : content;
        } catch (RuntimeException exception) {
            return content;
        }
    }
}
