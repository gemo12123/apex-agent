package org.gemo.apex.common.agent;

import org.gemo.apex.common.exception.DomainInvariantException;

import java.util.Set;

import static org.gemo.apex.common.support.DomainValues.immutableNames;

public record ToolSetDefinition(Set<String> availableTools, Set<String> defaultEnabledTools) {
    public ToolSetDefinition {
        availableTools = immutableNames(availableTools, "availableTools");
        defaultEnabledTools = immutableNames(defaultEnabledTools, "defaultEnabledTools");
        if (!availableTools.containsAll(defaultEnabledTools)) {
            throw new DomainInvariantException("defaultEnabledTools 必须是 availableTools 的子集");
        }
    }
}
