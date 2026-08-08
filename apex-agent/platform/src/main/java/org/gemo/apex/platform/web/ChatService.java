package org.gemo.apex.platform.web;

import java.util.concurrent.Executor;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.intervention.HumanResponseCommand;
import org.gemo.apex.platform.config.ApexAgentPlatformProperties;
import org.gemo.apex.platform.web.sse.RequestBoundAgentEventPublisherFactory;
import org.gemo.apex.platform.web.sse.SseEmitterAgentEventPublisher;
import org.gemo.apex.protocol.request.ChatRequest;
import org.gemo.apex.protocol.request.RequestType;
import org.gemo.apex.runtime.api.AgentPreparationException;
import org.gemo.apex.runtime.api.ApexAgentRuntime;
import org.gemo.apex.runtime.execution.ApexAgentExecution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * HTTP 请求到异步 Agent execution 的编排服务。
 *
 * <p>SSE 发布器在提交线程池前完成绑定，因此连接提前关闭、准备失败和线程池拒绝都能触发同一取消与 收尾路径。
 */
@Service
public class ChatService {
    private final ApexAgentRuntime runtime;
    private final RequestBoundAgentEventPublisherFactory publishers;
    private final Executor executor;
    private final long timeout;

    public ChatService(
            ApexAgentRuntime runtime,
            RequestBoundAgentEventPublisherFactory publishers,
            @Qualifier("agentExecutionExecutor") Executor executor,
            ApexAgentPlatformProperties properties) {
        this.runtime = runtime;
        this.publishers = publishers;
        this.executor = executor;
        this.timeout = properties.getSseTimeoutMillis();
    }

    /** 创建 SSE 通道、同步准备 execution，再将实际执行提交到带用户上下文的线程池。 */
    public SseEmitter chat(ChatRequest request, String userId) {
        SseEmitter emitter = new SseEmitter(timeout);
        SseEmitterAgentEventPublisher publisher = new SseEmitterAgentEventPublisher(emitter);
        ApexAgentExecution execution;
        try {
            execution =
                    publishers.prepare(
                            request.getSessionId(),
                            request.getAgentKey(),
                            userId,
                            publisher,
                            () -> prepare(request, userId));
        } catch (AgentPreparationException exception) {
            if (!exception.endPublished()) {
                throw exception;
            }
            publisher.completeFromExecution();
            return emitter;
        }
        publisher.bind(execution);
        if (publisher.isClosed()) {
            return emitter;
        }
        try {
            executor.execute(
                    () -> {
                        try {
                            execution.execute();
                        } finally {
                            publisher.completeFromExecution();
                        }
                    });
        } catch (TaskRejectedException exception) {
            execution.cancelBeforeStart();
            publisher.completeFromExecution();
        }
        return emitter;
    }

    /** 按请求类型映射为新会话命令或人工恢复命令。 */
    private ApexAgentExecution prepare(ChatRequest request, String userId) {
        if (request.getType() == RequestType.HUMAN_RESPONSE) {
            return runtime.resumeAgent(
                    new HumanResponseCommand(
                            request.getSessionId(),
                            request.getAgentKey(),
                            userId,
                            request.getHumanResponse()));
        }
        return runtime.newAgent(
                new AgentRequest(
                        request.getSessionId(), request.getAgentKey(), userId, request.getQuery()));
    }
}
