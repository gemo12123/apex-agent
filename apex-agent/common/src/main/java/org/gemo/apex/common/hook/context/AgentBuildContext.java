package org.gemo.apex.common.hook.context;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import org.gemo.apex.common.agent.AgentDefinitionSnapshot;
import org.gemo.apex.common.hook.HookBinding;

public record AgentBuildContext(
        String sessionId, HookBinding binding, AgentDefinitionSnapshot definition)
        implements HookContextView {
    public AgentBuildContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        definition = nonNull(definition, "definition");
    }
}
