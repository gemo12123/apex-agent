package org.gemo.apex.runtime;

import org.gemo.apex.common.exception.CancellationRequestedException;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.tool.CancellationToken;
import org.gemo.apex.common.tool.SubAgentCallTrace;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolDefinition;
import org.gemo.apex.common.tool.ToolExecutionContext;
import org.gemo.apex.extension.model.ModelStreamObserver;
import org.gemo.apex.extension.tool.ToolExecutionObserver;
import org.gemo.apex.protocol.event.AgentMessage;
import org.gemo.apex.protocol.event.EndMessage;
import org.gemo.apex.runtime.api.ApexAgentRuntime;
import org.gemo.apex.runtime.event.OnceAgentEventPublisher;
import org.gemo.apex.runtime.execution.RuntimeCancellationSource;
import org.gemo.apex.runtime.execution.SessionBusyException;
import org.gemo.apex.runtime.execution.SessionExecutionCoordinator;
import org.gemo.apex.runtime.execution.SessionExecutionLease;
import org.gemo.apex.runtime.mcp.McpAgentToolAdapter;
import org.gemo.apex.runtime.mcp.McpCallHandle;
import org.gemo.apex.runtime.mcp.McpTransport;
import org.gemo.apex.runtime.model.springai.SpringAiModelGateway;
import org.gemo.apex.runtime.subagent.HttpSubAgentTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeCancellationIntegrationTest {

    /**
     * SpringAi取消应释放订阅并唤醒等待线程
     */
    @Test
    void cancelsSpringAiSubscriptionAndWakesWaitingThread() throws Exception {
        AtomicBoolean disposed = new AtomicBoolean();
        CountDownLatch subscribed = new CountDownLatch(1);
        ChatModel model = (ChatModel) Proxy.newProxyInstance(ChatModel.class.getClassLoader(),
                new Class<?>[]{ChatModel.class}, (proxy, method, args) -> {
                    if (method.getName().equals("stream")) return Flux.create(ignored -> subscribed.countDown())
                            .doOnCancel(() -> disposed.set(true));
                    if (method.getName().equals("toString")) return "never-model";
                    throw new UnsupportedOperationException(method.toString());
                });
        RuntimeCancellationSource source = new RuntimeCancellationSource();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                new SpringAiModelGateway(model).stream(new ModelRequest("system", List.of(), List.of(), Map.of()),
                        observer(source.token()));
            } catch (Throwable error) {
                failure.set(error);
            }
        });

        assertTrue(subscribed.await(2, TimeUnit.SECONDS));
        source.cancel();
        worker.join(2_000);

        assertAll(
                () -> assertFalse(worker.isAlive()),
                () -> assertTrue(disposed.get()),
                () -> assertInstanceOf(CancellationRequestedException.class, failure.get()));
    }

    /**
     * Mcp取消应调用底层句柄并转换为统一取消语义
     */
    @Test
    void cancelsMcpUnderlyingHandleAndConvertsToUnifiedCancellationSemantics() throws Exception {
        RuntimeCancellationSource source = new RuntimeCancellationSource();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicInteger cancelCalls = new AtomicInteger();
        McpCallHandle handle = new McpCallHandle() {
            @Override public Map<String, Object> await() {
                entered.countDown();
                try {
                    assertTrue(cancelled.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("底层调用已取消");
            }
            @Override public void cancel() { cancelCalls.incrementAndGet(); cancelled.countDown(); }
        };
        McpTransport transport = new McpTransport() {
            @Override public void connect() { }
            @Override public List<ToolDefinition> listTools() { return List.of(); }
            @Override public McpCallHandle call(String name, Map<String, Object> arguments) { return handle; }
            @Override public void close() { }
        };
        var adapter = new McpAgentToolAdapter(tool("mcp/search"), transport);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                adapter.execute(call("mcp/search"), context(source.token()), toolObserver(source.token()));
            } catch (Throwable error) {
                failure.set(error);
            }
        });

        assertTrue(entered.await(2, TimeUnit.SECONDS));
        source.cancel();
        worker.join(2_000);

        assertAll(
                () -> assertEquals(1, cancelCalls.get()),
                () -> assertFalse(worker.isAlive()),
                () -> assertInstanceOf(CancellationRequestedException.class, failure.get()));
    }

    /**
     * HttpSubAgent取消应取消Future且不生成普通工具失败
     */
    @Test
    void cancelsHttpSubAgentFutureWithoutProducingOrdinaryToolFailure() throws Exception {
        CompletableFuture<HttpResponse<Stream<String>>> future = new CompletableFuture<>();
        RuntimeCancellationSource source = new RuntimeCancellationSource();
        HttpSubAgentTool adapter = new HttpSubAgentTool(tool("child"), new FakeHttpClient(future),
                URI.create("http://child.example"), "child", Duration.ofSeconds(10));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                adapter.execute(call("child"), context(source.token()), toolObserver(source.token()));
            } catch (Throwable error) {
                failure.set(error);
            }
        });

        source.cancel();
        worker.join(2_000);

        assertAll(
                () -> assertTrue(future.isCancelled()),
                () -> assertFalse(worker.isAlive()),
                () -> assertInstanceOf(CancellationRequestedException.class, failure.get()));
    }

    /**
     * SubAgent人工介入应停止子流且不透传交互事件
     */
    @Test
    void stopsSubAgentStreamAndDoesNotForwardInteractionEventsForHumanIntervention() {
        String askHuman = "{\"event_type\":\"ASK_HUMAN\",\"context\":{\"executor\":\"ask_human\",\"invocation_id\":\"i\",\"mode\":\"react\"},\"messages\":[{\"input_type\":\"TEXT_INPUT\",\"question\":\"Need input?\",\"options\":[],\"tool_call_id\":\"c\"}]}";
        CompletableFuture<HttpResponse<Stream<String>>> future = CompletableFuture.completedFuture(
                response(Stream.of("data: " + askHuman, "")));
        RuntimeCancellationSource source = new RuntimeCancellationSource();
        AtomicInteger forwarded = new AtomicInteger();
        HttpSubAgentTool adapter = new HttpSubAgentTool(tool("child"), new FakeHttpClient(future),
                URI.create("http://child.example"), "child", Duration.ofSeconds(10));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.execute(call("child"), context(source.token()), new ToolExecutionObserver() {
                    @Override public void onEvent(AgentMessage event) { forwarded.incrementAndGet(); }
                    @Override public CancellationToken cancellationToken() { return source.token(); }
                }));

        assertAll(
                () -> assertTrue(error.getMessage().contains("不支持透传恢复")),
                () -> assertEquals(0, forwarded.get()));
    }

    /**
     * 运行中取消和Runtime关闭应等待finally释放Lease与资源
     */
    @Test
    void awaitsFinallyToReleaseLeaseAndResourcesDuringCancellationAndRuntimeClose() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cancellationSeen = new CountDownLatch(1);
        CountDownLatch allowExit = new CountDownLatch(1);
        TrackingCoordinator coordinator = new TrackingCoordinator();
        AtomicInteger closed = new AtomicInteger();
        ApexAgentRuntime runtime = ApexAgentRuntime.builder().modelGateway((request, observer) -> {
                    entered.countDown();
                    try (var ignored = observer.cancellationToken().onCancel(cancellationSeen::countDown)) {
                        assertTrue(allowExit.await(2, TimeUnit.SECONDS));
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                    observer.cancellationToken().throwIfCancellationRequested();
                    throw new AssertionError("取消后不应继续模型结果");
                }).sessionExecutionCoordinator(coordinator).ownedResource(closed::incrementAndGet).build();
        var execution = runtime.newAgent(new AgentRequest("lease-session", "default", "u", "q"));
        Thread worker = Thread.ofVirtual().start(execution::run);

        assertTrue(entered.await(2, TimeUnit.SECONDS));
        runtime.close();
        assertTrue(cancellationSeen.await(2, TimeUnit.SECONDS));
        assertAll(
                () -> assertTrue(worker.isAlive()),
                () -> assertEquals(0, coordinator.releases.get()),
                () -> assertEquals(0, closed.get()),
                () -> assertThrows(SessionBusyException.class, () -> coordinator.acquire("lease-session")));

        allowExit.countDown();
        worker.join(2_000);
        assertAll(
                () -> assertFalse(worker.isAlive()),
                () -> assertEquals(1, coordinator.releases.get()),
                () -> assertEquals(1, closed.get()));
    }

    /**
     * OncePublisher应只发送一次End且发布失败触发取消
     */
    @Test
    void oncePublisherEmitsEndOnlyOnceAndCancelsOnPublishFailure() {
        RuntimeCancellationSource source = new RuntimeCancellationSource();
        AtomicInteger ends = new AtomicInteger();
        OnceAgentEventPublisher once = new OnceAgentEventPublisher(event -> {
            if (event instanceof EndMessage) ends.incrementAndGet();
        }, source);
        once.end();
        once.end();
        assertEquals(1, ends.get());

        RuntimeCancellationSource failedSource = new RuntimeCancellationSource();
        OnceAgentEventPublisher failed = new OnceAgentEventPublisher(event -> { throw new IllegalStateException("closed"); },
                failedSource);
        assertThrows(IllegalStateException.class, failed::end);
        assertTrue(failedSource.token().isCancellationRequested());
    }

    private static ModelStreamObserver observer(CancellationToken token) {
        return new ModelStreamObserver() {
            @Override public void onChunk(org.gemo.apex.common.model.ModelStreamChunk chunk) { }
            @Override public CancellationToken cancellationToken() { return token; }
        };
    }

    private static ToolExecutionObserver toolObserver(CancellationToken token) {
        return new ToolExecutionObserver() {
            @Override public void onEvent(AgentMessage event) { }
            @Override public CancellationToken cancellationToken() { return token; }
        };
    }

    private static ToolDefinition tool(String name) {
        return new ToolDefinition(name, name, "{}", Map.of());
    }

    private static ToolCall call(String name) {
        return new ToolCall("call-1", name, 0, Map.of("query", "hello"), Map.of());
    }

    private static ToolExecutionContext context(CancellationToken token) {
        return new ToolExecutionContext("session", 1, 1, "user", null,
                new SubAgentCallTrace("trace", List.of("parent"), 3), token, Map.of());
    }

    private static HttpResponse<Stream<String>> response(Stream<String> lines) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return 200; }
            @Override public HttpRequest request() { return HttpRequest.newBuilder(URI.create("http://child.example")).build(); }
            @Override public Optional<HttpResponse<Stream<String>>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
            @Override public Stream<String> body() { return lines; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return URI.create("http://child.example/api/sse/chat"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }

    private static final class FakeHttpClient extends HttpClient {
        private final CompletableFuture<HttpResponse<Stream<String>>> future;

        private FakeHttpClient(CompletableFuture<HttpResponse<Stream<String>>> future) { this.future = future; }
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException, InterruptedException { throw new UnsupportedOperationException(); }
        @SuppressWarnings("unchecked")
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                          HttpResponse.BodyHandler<T> handler) {
            return (CompletableFuture<HttpResponse<T>>) (CompletableFuture<?>) future;
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                          HttpResponse.BodyHandler<T> handler,
                                                                          HttpResponse.PushPromiseHandler<T> pushHandler) {
            return sendAsync(request, handler);
        }
    }

    private static final class TrackingCoordinator implements SessionExecutionCoordinator {
        private final AtomicBoolean held = new AtomicBoolean();
        private final AtomicInteger releases = new AtomicInteger();

        @Override public SessionExecutionLease acquire(String sessionId) {
            if (!held.compareAndSet(false, true)) throw new SessionBusyException(sessionId);
            return new SessionExecutionLease() {
                private final AtomicBoolean released = new AtomicBoolean();
                @Override public String sessionId() { return sessionId; }
                @Override public void release() {
                    if (released.compareAndSet(false, true)) {
                        releases.incrementAndGet();
                        held.set(false);
                    }
                }
            };
        }
    }
}
