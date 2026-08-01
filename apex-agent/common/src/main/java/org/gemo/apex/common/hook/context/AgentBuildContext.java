package org.gemo.apex.common.hook.context;

import org.gemo.apex.common.agent.AgentDefinitionSnapshot;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record AgentBuildContext(String sessionId, AgentDefinitionSnapshot definition) implements HookContextView {
    public AgentBuildContext {
        sessionId = required(sessionId, "sessionId");
        definition = nonNull(definition, "definition");
    }
}
