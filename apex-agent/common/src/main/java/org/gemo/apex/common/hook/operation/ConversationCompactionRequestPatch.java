package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.conversation.ConversationCompactionRequest;

public record ConversationCompactionRequestPatch(ConversationCompactionRequest replacement) {
    public ConversationCompactionRequestPatch {
        replacement = nonNull(replacement, "replacement");
    }
}
