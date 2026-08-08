package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.conversation.ConversationCompactionResult;

public record ConversationCompactionResultPatch(ConversationCompactionResult replacement) {
    public ConversationCompactionResultPatch {
        replacement = nonNull(replacement, "replacement");
    }
}
