package org.gemo.apex.platform.web;

import java.time.Instant;

public record SessionHistorySummary(
        String sessionId, String agentKey, String sessionSummary, String executionStatus, Instant lastActiveTime) {}
