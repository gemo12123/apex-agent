package org.gemo.apex.common.hook.result;

import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ToolResultPatch;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ContinuePostToolCall(HookMutations mutations, ToolResultPatch patch)
        implements PostToolCallHookResult {
    public ContinuePostToolCall {
        mutations = nonNull(mutations, "mutations");
        patch = nonNull(patch, "patch");
    }
}
