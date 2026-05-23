package org.gemo.apex.web.service;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.core.SuperAgentExecutor;
import org.gemo.apex.core.SuperAgentSessionService;
import org.gemo.apex.core.SessionExecutionGuard;
import org.gemo.apex.domain.dto.ChatRequest;
import org.gemo.apex.message.EndMessage;
import org.gemo.apex.util.JacksonUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Service
public class ChatStreamingApplicationService {
    static final String STREAM_CONTEXT_INIT_FAILED = "STREAM_CONTEXT_INIT_FAILED";
    static final String STREAM_EXECUTION_FAILED = "STREAM_EXECUTION_FAILED";
    static final String STREAM_TASK_SUBMISSION_FAILED = "STREAM_TASK_SUBMISSION_FAILED";

    @Autowired
    private SuperAgentSessionService sessionService;

    @Autowired
    private SuperAgentExecutor executor;

    @Autowired
    private ChatTerminalEventFactory terminalEventFactory;

    @Autowired
    private SessionExecutionGuard sessionExecutionGuard;

    @Autowired
    @Qualifier("chatStreamExecutor")
    private TaskExecutor chatStreamExecutor;

    public void stream(ChatRequest request, SseEmitter emitter) {
        try {
            chatStreamExecutor.execute(() -> runStream(request, emitter));
        } catch (TaskRejectedException ex) {
            log.error("Chat stream task submission failed, sessionId={}", request.getSessionId(), ex);
            sendTerminalEvent(emitter,
                    terminalEventFactory.buildForRequestFailure(STREAM_TASK_SUBMISSION_FAILED, ex.getMessage()));
            emitter.complete();
            sessionExecutionGuard.release(request.getSessionId());
        }
    }

    private void runStream(ChatRequest request, SseEmitter emitter) {
        SuperAgentContext sessionContext = null;
        boolean terminalSent = false;
        try {
            if (request.getType() == org.gemo.apex.constant.RequestType.HUMAN_RESPONSE) {
                sessionContext = sessionService.resumeContext(request.getSessionId(), request.getAgentKey(),
                        request.getHumanResponse());
            } else {
                sessionContext = sessionService.createContext(request.getSessionId(), request.getAgentKey(),
                        request.getQuery());
            }

            sessionContext.setSseEmitter(emitter);
            executor.execute(sessionContext);
        } catch (Exception ex) {
            log.error("SSE 执行异常, sessionId={}", request.getSessionId(), ex);
            EndMessage endMessage = sessionContext == null
                    ? terminalEventFactory.buildForRequestFailure(STREAM_CONTEXT_INIT_FAILED, ex.getMessage())
                    : terminalEventFactory.buildForFailure(sessionContext, STREAM_EXECUTION_FAILED, ex.getMessage());
            sendTerminalEvent(emitter, endMessage);
            terminalSent = true;
        } finally {
            try {
                if (!terminalSent && sessionContext != null) {
                    sendTerminalEvent(emitter, terminalEventFactory.buildForCompletion(sessionContext));
                }
            } finally {
                emitter.complete();
                sessionExecutionGuard.release(request.getSessionId());
                log.info("SSE 连接关闭, sessionId={}", request.getSessionId());
            }
        }
    }

    private void sendTerminalEvent(SseEmitter emitter, EndMessage endMessage) {
        try {
            emitter.send(JacksonUtils.toJson(endMessage));
        } catch (IOException sendFailure) {
            log.error("Failed to send terminal SSE event", sendFailure);
        }
    }
}
