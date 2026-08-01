package org.gemo.apex.common.hook.result;

import org.gemo.apex.common.hook.operation.HookMutations;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ContinueLoop(HookMutations mutations) implements LoopHookResult {
    public ContinueLoop { mutations = nonNull(mutations, "mutations"); }
}
