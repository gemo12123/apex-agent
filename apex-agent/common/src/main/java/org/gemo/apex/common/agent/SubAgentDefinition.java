package org.gemo.apex.common.agent;

import static org.gemo.apex.common.support.DomainValues.required;

public record SubAgentDefinition(String agentKey, String description) {
    public SubAgentDefinition {
        agentKey = required(agentKey, "agentKey");
        description = required(description, "description");
    }
}
