package org.gemo.apex.common.hook.result;

import org.gemo.apex.common.hook.operation.ConversationCompactionRequestPatch;
import org.gemo.apex.common.hook.operation.HookMutations;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ContinuePreMessageCompression(HookMutations mutations,
                                            ConversationCompactionRequestPatch patch)
        implements PreMessageCompressionHookResult {
    public ContinuePreMessageCompression {
        mutations = nonNull(mutations, "mutations");
        patch = nonNull(patch, "patch");
    }
}
