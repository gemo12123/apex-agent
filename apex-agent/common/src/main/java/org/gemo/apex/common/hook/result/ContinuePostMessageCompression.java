package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.hook.operation.ConversationCompactionResultPatch;
import org.gemo.apex.common.hook.operation.HookMutations;

public record ContinuePostMessageCompression(
        HookMutations mutations, ConversationCompactionResultPatch patch)
        implements PostMessageCompressionHookResult {
    public ContinuePostMessageCompression {
        mutations = nonNull(mutations, "mutations");
        patch = nonNull(patch, "patch");
    }
}
