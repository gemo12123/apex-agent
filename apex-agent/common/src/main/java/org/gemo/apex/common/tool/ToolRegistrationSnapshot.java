package org.gemo.apex.common.tool;

import static org.gemo.apex.common.support.DomainValues.immutableNames;

import java.util.Set;

public record ToolRegistrationSnapshot(Set<String> registeredTools) {
    public ToolRegistrationSnapshot {
        registeredTools = immutableNames(registeredTools, "registeredTools");
    }
}
