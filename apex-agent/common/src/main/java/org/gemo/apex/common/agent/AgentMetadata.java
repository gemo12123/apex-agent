package org.gemo.apex.common.agent;

import static org.gemo.apex.common.support.DomainValues.required;

public record AgentMetadata(String agentKey, String name, String description) {
    public AgentMetadata {
        agentKey = required(agentKey, "agentKey");
        name = required(name, "name");
        description = required(description, "description");
    }
}
