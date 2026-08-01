package org.gemo.apex.hook.lifecycle;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class HookDispatchResult {
    private final AgentHookResult result;
    @Builder.Default
    private final List<String> executedHookBeans = List.of();

    public static HookDispatchResult continued() {
        return builder().result(AgentHookResult.continueFlow()).build();
    }
}
