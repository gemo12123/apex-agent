package org.gemo.apex.platform.web.sse;

import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.extension.event.AgentEventPublisher;
import org.gemo.apex.protocol.event.AgentMessage;
import org.gemo.apex.runtime.execution.ApexAgentExecution;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 将协议消息序列化为 SSE，并把客户端断连反向传播为 execution 取消。
 */
public final class SseEmitterAgentEventPublisher implements AgentEventPublisher {
    private final SseEmitter emitter;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<ApexAgentExecution> execution = new AtomicReference<>();

    public SseEmitterAgentEventPublisher(SseEmitter emitter) {
        this.emitter = emitter;
        emitter.onCompletion(this::transportClosed);
        emitter.onTimeout(this::transportClosed);
        emitter.onError(ignored -> transportClosed());
    }

    @Override
    /** 发送单条协议消息；底层发送失败等同于传输已关闭。 */
    public void publish(AgentMessage message) {
        if (closed.get()) throw new IllegalStateException("SSE 已关闭");
        try {
            emitter.send(JsonUtils.toJson(message));
        } catch (IOException | IllegalStateException exception) {
            transportClosed();
            throw new IllegalStateException("SSE 发布失败", exception);
        }
    }

    /** 绑定后若 SSE 已断开，立即取消刚准备好的 execution，避免后台孤儿执行。 */
    public void bind(ApexAgentExecution value) {
        if (!execution.compareAndSet(null, value)) throw new IllegalStateException("execution 已绑定");
        if (closed.get()) cancelQuietly(value);
    }

    public boolean isClosed() { return closed.get(); }

    public void completeFromExecution() {
        closed.set(true);
        emitter.complete();
    }

    /** SSE 完成、超时或异常时统一关闭发送端并请求取消。 */
    void transportClosed() {
        closed.set(true);
        ApexAgentExecution value = execution.get();
        if (value != null) cancelQuietly(value);
    }

    private void cancelQuietly(ApexAgentExecution value) {
        try {
            value.cancel();
        } catch (RuntimeException ignored) {
            // 传输已关闭，取消路径不能再向客户端传播错误。
        }
    }
}
