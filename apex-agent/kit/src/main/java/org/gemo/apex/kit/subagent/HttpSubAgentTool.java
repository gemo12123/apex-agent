package org.gemo.apex.kit.subagent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.gemo.apex.common.exception.CancellationRequestedException;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolDefinition;
import org.gemo.apex.common.tool.ToolExecutionContext;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.extension.tool.AgentTool;
import org.gemo.apex.extension.tool.ToolExecutionObserver;
import org.gemo.apex.protocol.event.AgentMessage;
import org.gemo.apex.protocol.event.HumanInterventionMessage;
import org.gemo.apex.protocol.event.InvocationChangeMessage;
import org.gemo.apex.protocol.event.InvocationDeclaredMessage;
import org.gemo.apex.protocol.event.StreamContentMessage;
import org.gemo.apex.protocol.request.ChatRequest;
import org.gemo.apex.protocol.request.RequestType;

public final class HttpSubAgentTool implements AgentTool {
    private final ToolDefinition d;
    private final HttpClient client;
    private final URI endpoint;
    private final String key;
    private final Duration timeout;

    public HttpSubAgentTool(ToolDefinition d, HttpClient c, URI e, String k, Duration t) {
        this.d = d;
        client = c;
        endpoint = e;
        key = k;
        timeout = t;
    }

    public ToolDefinition definition() {
        return d;
    }

    public ToolResult execute(ToolCall call, ToolExecutionContext x, ToolExecutionObserver o) {
        var trace = x.subAgentCallTrace();
        if (trace != null) {
            if (trace.agentKeys().contains(key)) {
                throw new IllegalStateException("SubAgent 调用闭环");
            }
            trace.append(key);
        }
        var body = new ChatRequest();
        body.setType(RequestType.NEW);
        body.setAgentKey(key);
        body.setSessionId(UUID.randomUUID().toString());
        body.setQuery(Objects.toString(call.arguments().get("query"), ""));
        var req =
                HttpRequest.newBuilder(endpoint.resolve("/api/sse/chat"))
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                        .header("X-User-Id", x.userId())
                        .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(body)))
                        .build();
        var future = client.sendAsync(req, HttpResponse.BodyHandlers.ofLines());
        try (var reg = x.cancellationToken().onCancel(() -> future.cancel(true))) {
            try {
                var res = future.join();
                if (res.statusCode() / 100 != 2) {
                    throw new IllegalStateException("SubAgent HTTP " + res.statusCode());
                }
                StringBuilder content = new StringBuilder();
                var decoder = new SseEventDecoder();
                try (Stream<String> lines = res.body();
                        var close = x.cancellationToken().onCancel(lines::close)) {
                    lines.forEach(
                            l -> {
                                x.cancellationToken().throwIfCancellationRequested();
                                decoder.accept(l).forEach(j -> consume(j, content, o, future));
                            });
                    decoder.finish().forEach(j -> consume(j, content, o, future));
                }
                return new ToolResult(
                        call.toolCallId(),
                        call.name(),
                        content.toString(),
                        Map.of("childSessionId", body.getSessionId()));
            } catch (RuntimeException e) {
                if (x.cancellationToken().isCancellationRequested()) {
                    throw new CancellationRequestedException();
                }
                throw e;
            }
        }
    }

    private void consume(
            String j, StringBuilder c, ToolExecutionObserver o, CompletableFuture<?> f) {
        AgentMessage m = JsonUtils.fromJson(j, AgentMessage.class);
        if (m instanceof HumanInterventionMessage) {
            f.cancel(true);
            throw new IllegalStateException("子智能体请求人工介入，当前工具调用不支持透传恢复");
        }
        if (m instanceof StreamContentMessage s && s.getMessages() != null) {
            s.getMessages().forEach(p -> c.append(p.getContent()));
        } else if (m instanceof InvocationDeclaredMessage || m instanceof InvocationChangeMessage) {
            o.onEvent(m);
        }
    }
}
