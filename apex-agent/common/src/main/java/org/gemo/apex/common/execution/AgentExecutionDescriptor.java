package org.gemo.apex.common.execution;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record AgentExecutionDescriptor(
        String executionId, String sessionId, String agentKey, String userId, RequestKind kind) {
    public AgentExecutionDescriptor {
        executionId = required(executionId, "executionId");
        sessionId = required(sessionId, "sessionId");
        agentKey = required(agentKey, "agentKey");
        userId = required(userId, "userId");
        kind = nonNull(kind, "kind");
    }
}
