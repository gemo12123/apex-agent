package org.gemo.apex.common.conversation;

import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.support.DomainValues;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.required;

public record ConversationCompactionResult(String compactionId, String summary,
                                           List<AgentMessageEntry> retainedMessages,
                                           Map<String, Object> metadata) {
    public ConversationCompactionResult {
        compactionId = required(compactionId, "compactionId");
        summary = required(summary, "summary");
        retainedMessages = immutableList(retainedMessages, "retainedMessages");
        Set<String> entryIds = new HashSet<>();
        long previousSortNo = -1;
        for (AgentMessageEntry message : retainedMessages) {
            if (!entryIds.add(message.entryId())) {
                throw new IllegalArgumentException("retainedMessages.entryId 不能重复");
            }
            if (message.sortNo() <= previousSortNo) {
                throw new IllegalArgumentException("retainedMessages 必须按 sortNo 严格递增");
            }
            previousSortNo = message.sortNo();
        }
        metadata = DomainValues.jsonMap(metadata, "metadata");
    }
}
