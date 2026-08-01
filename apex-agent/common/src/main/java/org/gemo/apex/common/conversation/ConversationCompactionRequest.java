package org.gemo.apex.common.conversation;

import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.support.DomainValues;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gemo.apex.common.support.DomainValues.*;

public record ConversationCompactionRequest(String sessionId, String compactionId,
                                            List<AgentMessageEntry> sourceMessages,
                                            List<AgentMessageEntry> retainedMessages,
                                            Map<String, Object> metadata) {
    public ConversationCompactionRequest {
        sessionId = required(sessionId, "sessionId");
        compactionId = required(compactionId, "compactionId");
        sourceMessages = immutableList(sourceMessages, "sourceMessages");
        if (sourceMessages.isEmpty()) {
            throw new IllegalArgumentException("sourceMessages 不能为空");
        }
        for (AgentMessageEntry message : sourceMessages) {
            if (!sessionId.equals(message.sessionId())) {
                throw new IllegalArgumentException("sourceMessages 必须属于 sessionId");
            }
        }
        retainedMessages = immutableList(retainedMessages, "retainedMessages");
        validateOrderedSubset(sourceMessages, retainedMessages);
        metadata = DomainValues.jsonMap(metadata, "metadata");
    }

    private static void validateOrderedSubset(List<AgentMessageEntry> source,
                                              List<AgentMessageEntry> retained) {
        Set<String> sourceIds = new HashSet<>();
        long sourcePreviousSortNo = -1;
        for (AgentMessageEntry message : source) {
            if (!sourceIds.add(message.entryId())) {
                throw new IllegalArgumentException("sourceMessages.entryId 不能重复");
            }
            if (message.sortNo() <= sourcePreviousSortNo) {
                throw new IllegalArgumentException("sourceMessages 必须按 sortNo 严格递增");
            }
            sourcePreviousSortNo = message.sortNo();
        }
        long previousSortNo = -1;
        for (AgentMessageEntry message : retained) {
            if (!sourceIds.contains(message.entryId())) {
                throw new IllegalArgumentException("retainedMessages 必须是 sourceMessages 的子集");
            }
            if (message.sortNo() <= previousSortNo) {
                throw new IllegalArgumentException("retainedMessages 必须按 sortNo 严格递增");
            }
            previousSortNo = message.sortNo();
        }
    }
}
