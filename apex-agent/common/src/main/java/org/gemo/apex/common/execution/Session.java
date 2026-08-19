package org.gemo.apex.common.execution;

import static org.gemo.apex.common.support.DomainValues.*;

import java.util.Set;

public record Session(
        String sessionId,
        String userId,
        String agentKey,
        SessionStatus status,
        long currentTurnNo,
        Set<String> enabledTools,
        Set<String> activatedSkills) {
    public Session {
        sessionId = required(sessionId, "sessionId");
        userId = required(userId, "userId");
        agentKey = required(agentKey, "agentKey");
        status = nonNull(status, "status");
        nonNegative(currentTurnNo, "currentTurnNo");
        enabledTools = immutableNames(enabledTools, "enabledTools");
        activatedSkills = immutableNames(activatedSkills, "activatedSkills");
    }
}
