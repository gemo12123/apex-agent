package org.gemo.apex.common.hook.result;

import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ToolCallPatch;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ContinuePreToolCall(HookMutations mutations, ToolCallPatch patch)
        implements PreToolCallHookResult {
    public ContinuePreToolCall {
        mutations = nonNull(mutations, "mutations");
        patch = nonNull(patch, "patch");
    }
}
