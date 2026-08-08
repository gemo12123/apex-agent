package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ModelRequestPatch;

public record ContinuePreModelCall(HookMutations mutations, ModelRequestPatch patch)
        implements PreModelCallHookResult {
    public ContinuePreModelCall {
        mutations = nonNull(mutations, "mutations");
        patch = nonNull(patch, "patch");
    }
}
