package org.gemo.apex.common.agent;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record AgentDefinitionSnapshot(AgentDefinition definition) {
    public AgentDefinitionSnapshot {
        definition = nonNull(definition, "definition");
    }
}
