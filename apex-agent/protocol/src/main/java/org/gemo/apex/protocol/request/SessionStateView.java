package org.gemo.apex.protocol.request;

import org.gemo.apex.protocol.event.HumanInterventionMessage;

public record SessionStateView(
        String sessionId,
        String agentKey,
        String executionStatus,
        HumanInterventionMessage pendingInteraction) {}
