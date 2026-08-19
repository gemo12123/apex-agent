package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.required;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.gemo.apex.common.message.AgentMessageEntry;

public record ConversationCompactionCommit(
        String sessionId,
        ConversationSummary summary,
        List<AgentMessageEntry> retainedMessages) {
    public ConversationCompactionCommit {
        sessionId = required(sessionId, "sessionId");
        summary = org.gemo.apex.common.support.DomainValues.nonNull(summary, "summary");
        retainedMessages = immutableList(retainedMessages, "retainedMessages");
        Set<String> retainedIds = new HashSet<>();
        long previousSortNo = -1;
        for (AgentMessageEntry message : retainedMessages) {
            if (!sessionId.equals(message.sessionId())) {
                throw new IllegalArgumentException("retainedMessages 必须属于 sessionId");
            }
            if (message.sortNo() <= summary.sourceEndSortNo()) {
                throw new IllegalArgumentException("retainedMessages 必须位于摘要覆盖范围之后");
            }
            if (message.messageType() == org.gemo.apex.common.message.MessageType.SUMMARY) {
                throw new IllegalArgumentException("retainedMessages 不能包含 SUMMARY");
            }
            if (!retainedIds.add(message.entryId())) {
                throw new IllegalArgumentException("retainedMessages.entryId 不能重复");
            }
            if (message.sortNo() <= previousSortNo) {
                throw new IllegalArgumentException("retainedMessages 必须按 sortNo 严格递增");
            }
            previousSortNo = message.sortNo();
        }
    }
}
