package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.hook.operation.HookMutations;

public record ContinuePreModelCall(HookMutations mutations) implements PreModelCallHookResult {
    public ContinuePreModelCall {
        mutations = nonNull(mutations, "mutations");
    }
}
