package org.gemo.apex.common.hook.operation;

import org.gemo.apex.common.conversation.ConversationCompactionResult;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record ConversationCompactionResultPatch(ConversationCompactionResult replacement) {
    public ConversationCompactionResultPatch { replacement = nonNull(replacement, "replacement"); }
}
