package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.gemo.apex.common.hook.operation.AppendConversationMessage;
import org.gemo.apex.common.hook.operation.ConversationCompactionResultPatch;
import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.support.DomainValues;

public record ContinuePostMessageCompression(
        HookMutations mutations,
        ConversationCompactionResultPatch patch,
        List<AppendConversationMessage> conversationAppends)
        implements PostMessageCompressionHookResult {
    public ContinuePostMessageCompression {
        mutations = nonNull(mutations, "mutations");
        patch = nonNull(patch, "patch");
        conversationAppends = DomainValues.immutableList(conversationAppends, "conversationAppends");
        Set<String> operationIds = new HashSet<>();
        for (AppendConversationMessage append : conversationAppends) {
            if (!operationIds.add(append.operationId())) {
                throw new IllegalArgumentException(
                        "conversationAppends.operationId 重复: " + append.operationId());
            }
        }
    }

    public ContinuePostMessageCompression(
            HookMutations mutations, ConversationCompactionResultPatch patch) {
        this(mutations, patch, List.of());
    }
}
