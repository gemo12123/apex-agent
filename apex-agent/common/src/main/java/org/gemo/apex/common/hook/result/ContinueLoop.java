package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.hook.operation.HookMutations;

public record ContinueLoop(HookMutations mutations) implements LoopHookResult {
    public ContinueLoop {
        mutations = nonNull(mutations, "mutations");
    }
}
