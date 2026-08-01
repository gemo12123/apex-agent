package org.gemo.apex.common.hook.result;

import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ModelResponsePatch;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ContinuePostModelCall(HookMutations mutations, ModelResponsePatch patch)
        implements PostModelCallHookResult {
    public ContinuePostModelCall {
        mutations = nonNull(mutations, "mutations");
        patch = nonNull(patch, "patch");
    }
}
