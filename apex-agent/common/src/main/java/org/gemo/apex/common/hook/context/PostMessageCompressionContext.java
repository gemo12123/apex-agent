package org.gemo.apex.common.hook.context;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import java.util.List;
import org.gemo.apex.common.conversation.ConversationCompactionResult;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.message.AgentMessageEntry;

public record PostMessageCompressionContext(
        String sessionId,
        HookBinding binding,
        List<AgentMessageEntry> originalMessages,
        ConversationCompactionResult result)
        implements HookContextView {
    public PostMessageCompressionContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        originalMessages = immutableList(originalMessages, "originalMessages");
        result = nonNull(result, "result");
    }
}
