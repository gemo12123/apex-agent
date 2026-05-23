package org.gemo.apex.core;

import org.gemo.apex.context.SuperAgentContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SuperAgentFactory {

    @Autowired
    private SuperAgentSessionService sessionService;

    @Autowired
    private SuperAgentExecutor executor;

    public SuperAgentContext createContext(String sessionId, String agentKey, String userQuery) {
        return sessionService.createContext(sessionId, agentKey, userQuery);
    }

    public SuperAgentContext resumeContext(String sessionId, String agentKey, Map<String, Object> humanResponse) {
        return sessionService.resumeContext(sessionId, agentKey, humanResponse);
    }

    public void executeContext(SuperAgentContext context) {
        executor.execute(context);
    }
}
