package org.gemo.apex.hook.lifecycle;

@FunctionalInterface
public interface AgentLifecycleHook {
    AgentHookResult apply(AgentHookContext context);
}
