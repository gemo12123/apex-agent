package org.gemo.apex.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.constant.ContextKeyEnum;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.RequestType;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.core.SuperAgentFactory;
import org.gemo.apex.domain.dto.ChatRequest;
import org.gemo.apex.exception.HumanInTheLoopException;
import org.gemo.apex.memory.context.UserContextHolder;
import org.gemo.apex.message.EndMessage;
import org.gemo.apex.util.MessageUtils;
import org.gemo.apex.config.provider.AgentConfigProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SSE 控制器。
 */
@Slf4j
@RestController
@RequestMapping("/api/sse")
public class ChatController {

    @Autowired
    private SuperAgentFactory superAgentFactory;

    @Autowired
    private AgentConfigProvider agentConfigProvider;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @GetMapping(value = "/agents", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getAgents() {
        java.util.List<Map<String, String>> data = agentConfigProvider.getAllAgents().stream()
                .map(agent -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("agentKey", agent.getAgentKey());
                    map.put("name", agent.getName());
                    return map;
                })
                .toList();
        return Map.of("code", 200, "data", data, "message", "success");
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeWithSse(@RequestBody ChatRequest request) {
        boolean isResume = request.getType() == RequestType.HUMAN_RESPONSE;
        String userId = UserContextHolder.getUserId();
        log.info("{} SSE 请求，sessionId={}, type={}, agentKey={}, userId={}",
                isResume ? "恢复" : "新建", request.getSessionId(), request.getType(), request.getAgentKey(), userId);

        SseEmitter emitter = new SseEmitter(600000L);
        executorService.submit(() -> {
            UserContextHolder.setUserId(userId);
            SuperAgentContext sessionContext = null;
            try {
                if (isResume) {
                    sessionContext = superAgentFactory.resumeContext(request.getSessionId(), request.getHumanResponse());
                    if (sessionContext == null) {
                        log.warn("未找到可恢复的挂起会话, sessionId={}", request.getSessionId());
                        emitter.complete();
                        return;
                    }
                } else {
                    sessionContext = superAgentFactory.createContext(request.getSessionId(), request.getAgentKey(),
                            request.getQuery());
                }

                sessionContext.setSseEmitter(emitter);
                superAgentFactory.executeContext(sessionContext);
            } catch (HumanInTheLoopException e) {
                log.info("会话挂起等待用户回复, sessionId={}, reason={}", request.getSessionId(), e.getMessage());
            } catch (Exception e) {
                log.error("SSE 执行异常, sessionId={}", request.getSessionId(), e);
            } finally {
                try {
                    if (sessionContext != null) {
                        MessageUtils.sendMessage(sessionContext, EndMessage.builder()
                                .context(buildEndContext(sessionContext))
                                .build());
                    }
                } finally {
                    emitter.complete();
                    UserContextHolder.clear();
                    log.info("SSE 连接关闭, sessionId={}", request.getSessionId());
                }
            }
        });

        return emitter;
    }

    private Map<String, Object> buildEndContext(SuperAgentContext context) {
        if (context.getCurrentStage() == SuperAgentContext.Stage.THINKING) {
            return Map.of();
        }
        Map<String, Object> map = new HashMap<>();
        map.put(ContextKeyEnum.MODE.getKey(),
                context.getExecutionMode() != null ? context.getExecutionMode().getMode() : "");
        if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR && context.getCurrentStageId() != null) {
            map.put(ContextKeyEnum.STAGE_ID.getKey(), context.getCurrentStageId());
        }
        return Map.copyOf(map);
    }
}
