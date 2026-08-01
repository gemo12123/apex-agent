package org.gemo.apex.common.snapshot;

import org.gemo.apex.common.tool.ToolOrigin;

import java.time.Instant;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record HistoricalToolBinding(String toolName, ToolOrigin origin, String sourceId,
                                    String reasonCode, Instant disabledTime) {
    public HistoricalToolBinding {
        toolName = required(toolName, "toolName");
        origin = nonNull(origin, "origin");
        sourceId = required(sourceId, "sourceId");
        reasonCode = required(reasonCode, "reasonCode");
        disabledTime = nonNull(disabledTime, "disabledTime");
    }

    public String identity() {
        return toolName + '\u0000' + origin + '\u0000' + sourceId;
    }
}
