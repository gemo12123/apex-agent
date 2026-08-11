package org.gemo.apex.common.hook.context;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import org.gemo.apex.common.conversation.ConversationCompactionCheck;
import org.gemo.apex.common.conversation.ConversationCompactionRequest;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.shared.SharedDataStore;

public record PreMessageCompressionContext(
        String sessionId,
        HookBinding binding,
        ModelRequest baseModelRequest,
        ConversationCompactionCheck check,
        ConversationCompactionRequest request,
        SharedDataStore sharedData)
        implements HookContextView {
    public PreMessageCompressionContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        baseModelRequest = nonNull(baseModelRequest, "baseModelRequest");
        check = nonNull(check, "check");
        request = nonNull(request, "request");
        sharedData = nonNull(sharedData, "sharedData");
        if (!sessionId.equals(check.triggerContext().sessionId())
                || !sessionId.equals(request.sessionId())) {
            throw new IllegalArgumentException("压缩上下文 sessionId 必须一致");
        }
    }
}
