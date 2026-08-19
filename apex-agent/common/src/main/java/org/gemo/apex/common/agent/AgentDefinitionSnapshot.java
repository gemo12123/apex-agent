package org.gemo.apex.common.agent;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.nonNull;

import java.util.List;

public record AgentDefinitionSnapshot(
        AgentDefinition definition, List<PrefixDeveloperMessage> prefixDeveloperMessages) {
    public AgentDefinitionSnapshot(AgentDefinition definition) {
        this(definition, List.of());
    }

    public AgentDefinitionSnapshot {
        definition = nonNull(definition, "definition");
        prefixDeveloperMessages = immutableList(prefixDeveloperMessages, "prefixDeveloperMessages");
    }
}
