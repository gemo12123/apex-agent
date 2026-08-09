package org.gemo.apex.common.tool;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import java.time.Instant;

public record UnavailableToolSource(
        ToolOrigin origin,
        String sourceId,
        String stableNamePrefix,
        String reasonCode,
        Instant observedAt) {
    public UnavailableToolSource {
        origin = nonNull(origin, "origin");
        if (origin != ToolOrigin.SUB_AGENT) {
            throw new IllegalArgumentException("origin 只允许 SUB_AGENT");
        }
        sourceId = required(sourceId, "sourceId");
        stableNamePrefix = required(stableNamePrefix, "stableNamePrefix");
        reasonCode = required(reasonCode, "reasonCode");
        observedAt = nonNull(observedAt, "observedAt");
    }

    public boolean matches(ToolOrigin candidateOrigin, String candidateSourceId, String toolName) {
        return origin == candidateOrigin
                && sourceId.equals(candidateSourceId)
                && toolName != null
                && toolName.startsWith(stableNamePrefix);
    }
}
