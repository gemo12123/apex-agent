package org.gemo.apex.common.hook.context;

import org.gemo.apex.common.conversation.ConversationCompactionResult;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record PostMessageCompressionContext(String sessionId, ConversationCompactionResult result)
        implements HookContextView {
    public PostMessageCompressionContext { sessionId = required(sessionId, "sessionId"); result = nonNull(result, "result"); }
}
