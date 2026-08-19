package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.hook.operation.ConversationCompactionRequestPatch;
import org.gemo.apex.common.hook.operation.HookMutations;

public record ContinuePreMessageCompression(
        HookMutations mutations, ConversationCompactionRequestPatch patch)
        implements PreMessageCompressionHookResult {
    public ContinuePreMessageCompression {
        mutations = nonNull(mutations, "mutations");
        patch = nonNull(patch, "patch");
    }
}
