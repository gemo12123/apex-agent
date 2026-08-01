package org.gemo.apex.common.agent;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record AddHookBinding(HookPoint hookPoint, HookBinding binding) implements AgentDefinitionOperation {
    public AddHookBinding {
        hookPoint = nonNull(hookPoint, "hookPoint");
        binding = nonNull(binding, "binding");
    }
}
