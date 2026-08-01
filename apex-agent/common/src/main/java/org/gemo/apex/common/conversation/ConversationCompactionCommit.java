package org.gemo.apex.common.conversation;

import org.gemo.apex.common.message.AgentMessageEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.required;

public record ConversationCompactionCommit(String sessionId, String compactionId,
                                           long sourceStartSortNo, long sourceEndSortNo,
                                           String summary, List<String> retainedEntryIds,
                                           List<AgentMessageEntry> finalMessages) {
    public ConversationCompactionCommit {
        sessionId = required(sessionId, "sessionId");
        compactionId = required(compactionId, "compactionId");
        if (sourceStartSortNo < 0 || sourceEndSortNo < sourceStartSortNo) {
            throw new IllegalArgumentException("压缩来源边界非法");
        }
        summary = required(summary, "summary");
        retainedEntryIds = immutableList(retainedEntryIds, "retainedEntryIds");
        if (new HashSet<>(retainedEntryIds).size() != retainedEntryIds.size()) {
            throw new IllegalArgumentException("retainedEntryIds 不能重复");
        }
        finalMessages = immutableList(finalMessages, "finalMessages");
        Set<String> finalIds = new HashSet<>();
        long previousSortNo = -1;
        for (AgentMessageEntry message : finalMessages) {
            if (!sessionId.equals(message.sessionId())) {
                throw new IllegalArgumentException("finalMessages 必须属于 sessionId");
            }
            if (!finalIds.add(message.entryId())) {
                throw new IllegalArgumentException("finalMessages.entryId 不能重复");
            }
            if (message.sortNo() <= previousSortNo) {
                throw new IllegalArgumentException("finalMessages 必须按 sortNo 严格递增");
            }
            previousSortNo = message.sortNo();
        }
        if (!finalIds.containsAll(retainedEntryIds)) {
            throw new IllegalArgumentException("retainedEntryIds 必须存在于 finalMessages");
        }
    }
}
