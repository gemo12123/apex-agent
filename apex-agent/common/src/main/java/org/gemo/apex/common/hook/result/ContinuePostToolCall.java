package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ToolResultPatch;

public record ContinuePostToolCall(HookMutations mutations, ToolResultPatch patch)
        implements PostToolCallHookResult {
    public ContinuePostToolCall {
        mutations = nonNull(mutations, "mutations");
        patch = nonNull(patch, "patch");
    }
}
