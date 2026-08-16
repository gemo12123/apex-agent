package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import java.util.List;
import java.util.Optional;
import org.gemo.apex.common.message.AgentMessageEntry;

/** Repository 返回的未压缩对话消息与当前累计摘要。 */
public record ConversationHistory(
        String sessionId, Optional<ConversationSummary> summary, List<AgentMessageEntry> messages) {
    public ConversationHistory {
        sessionId = required(sessionId, "sessionId");
        summary = nonNull(summary, "summary");
        messages = immutableList(messages, "messages");
        long previousSortNo = -1;
        for (AgentMessageEntry message : messages) {
            if (!sessionId.equals(message.sessionId())) {
                throw new IllegalArgumentException("历史消息必须属于同一 sessionId");
            }
            if (message.sortNo() <= previousSortNo) {
                throw new IllegalArgumentException("历史消息必须按 sortNo 严格递增");
            }
            previousSortNo = message.sortNo();
        }
    }
}
