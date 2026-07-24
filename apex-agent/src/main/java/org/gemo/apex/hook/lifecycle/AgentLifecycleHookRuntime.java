package org.gemo.apex.hook.lifecycle;

import java.util.Set;

public interface AgentLifecycleHookRuntime {
    HookDispatchResult run(HookPoint point, AgentRuntimeContext context, Set<String> skippedHookBeans);

    default HookDispatchResult run(HookPoint point, AgentRuntimeContext context) {
        return run(point, context, Set.of());
    }
}
