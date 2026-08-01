package org.gemo.apex.common.tool;

import java.util.List;
import java.util.Set;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.immutableNames;

public record ToolAvailabilitySnapshot(Set<String> unavailableToolNames,
                                       List<UnavailableToolSource> unavailableSources) {
    public ToolAvailabilitySnapshot {
        unavailableToolNames = immutableNames(unavailableToolNames, "unavailableToolNames");
        unavailableSources = immutableList(unavailableSources, "unavailableSources");
    }

    public boolean isUnavailable(String toolName, ToolOrigin origin, String sourceId) {
        return unavailableToolNames.contains(toolName)
                || unavailableSources.stream().anyMatch(source -> source.matches(origin, sourceId, toolName));
    }
}
