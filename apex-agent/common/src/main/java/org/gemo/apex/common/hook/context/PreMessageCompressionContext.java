package org.gemo.apex.common.hook.context;

import org.gemo.apex.common.conversation.ConversationCompactionRequest;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record PreMessageCompressionContext(String sessionId, ConversationCompactionRequest request)
        implements HookContextView {
    public PreMessageCompressionContext { sessionId = required(sessionId, "sessionId"); request = nonNull(request, "request"); }
}
