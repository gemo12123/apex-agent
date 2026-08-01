package org.gemo.apex.common.agent;

import org.gemo.apex.common.hook.HookPoint;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record RemoveHookBinding(HookPoint hookPoint, String bindingId) implements AgentDefinitionOperation {
    public RemoveHookBinding {
        hookPoint = nonNull(hookPoint, "hookPoint");
        bindingId = required(bindingId, "bindingId");
    }
}
