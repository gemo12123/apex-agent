package org.gemo.apex.protocol.request;

import org.gemo.apex.protocol.event.AgentMessage;

public record SessionStateView(
        String sessionId,
        String agentKey,
        String executionStatus,
        AgentMessage pendingInteraction) {
}
