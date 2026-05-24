package org.gemo.apex.core;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.domain.dto.ChatRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Service
public class SuperAgentFactory {

    @Autowired
    private SuperAgentSessionService sessionService;

    @Autowired
    private SuperAgentExecutor executor;

    @Autowired
    private SuperAgent superAgent;

    public SuperAgentContext createContext(String sessionId, String agentKey, String userQuery) {
        return sessionService.createContext(sessionId, agentKey, userQuery);
    }

    public SuperAgentContext resumeContext(String sessionId, String agentKey, Map<String, Object> humanResponse) {
        return sessionService.resumeContext(sessionId, agentKey, humanResponse);
    }

    public void executeContext(SuperAgentContext context) {
        executor.execute(context);
    }

    public SuperAgent create(ChatRequest request, SseEmitter emitter) {
        SuperAgentContext context = request.getType() == org.gemo.apex.constant.RequestType.HUMAN_RESPONSE
                ? resumeContext(request.getSessionId(), request.getAgentKey(), request.getHumanResponse())
                : createContext(request.getSessionId(), request.getAgentKey(), request.getQuery());
        context.setSseEmitter(emitter);
        superAgent.bindContext(context);
        return superAgent;
    }
}
