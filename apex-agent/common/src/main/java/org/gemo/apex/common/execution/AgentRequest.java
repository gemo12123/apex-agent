package org.gemo.apex.common.execution;

import static org.gemo.apex.common.support.DomainValues.required;

public record AgentRequest(String sessionId, String agentKey, String userId, String query) {
    public AgentRequest {
        sessionId = required(sessionId, "sessionId");
        agentKey = required(agentKey, "agentKey");
        userId = required(userId, "userId");
        query = required(query, "query");
    }
}
