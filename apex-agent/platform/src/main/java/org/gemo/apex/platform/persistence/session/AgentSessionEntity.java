package org.gemo.apex.platform.persistence.session;

import java.time.Instant;

public record AgentSessionEntity(String sessionId, String userId, String agentKey, String status,
                                 long currentTurnNo, String agentDefinitionSnapshot,
                                 String enabledToolNames, String activatedSkillNames,
                                 String runtimeSnapshot, String suspendedToolCall,
                                 Instant lastActiveTime) {
}
