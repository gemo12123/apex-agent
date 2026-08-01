package org.gemo.apex.common.hook.operation;

import org.gemo.apex.common.conversation.ConversationCompactionRequest;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ConversationCompactionRequestPatch(ConversationCompactionRequest replacement) {
    public ConversationCompactionRequestPatch { replacement = nonNull(replacement, "replacement"); }
}
