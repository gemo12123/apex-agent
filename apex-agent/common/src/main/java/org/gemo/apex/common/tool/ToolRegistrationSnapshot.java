package org.gemo.apex.common.tool;

import java.util.Set;

import static org.gemo.apex.common.support.DomainValues.immutableNames;

public record ToolRegistrationSnapshot(Set<String> registeredTools) {
    public ToolRegistrationSnapshot {
        registeredTools = immutableNames(registeredTools, "registeredTools");
    }
}
