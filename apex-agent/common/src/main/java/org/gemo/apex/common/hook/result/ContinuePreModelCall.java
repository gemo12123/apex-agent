package org.gemo.apex.common.hook.result;

import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ModelRequestPatch;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ContinuePreModelCall(HookMutations mutations, ModelRequestPatch patch)
        implements PreModelCallHookResult {
    public ContinuePreModelCall {
        mutations = nonNull(mutations, "mutations");
        patch = nonNull(patch, "patch");
    }
}
