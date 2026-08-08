package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.nonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.gemo.apex.common.message.AgentMessageEntry;

public record ConversationCompactionCheck(
        List<AgentMessageEntry> messages,
        long messageTokenEstimate,
        long messageCharacterEstimate,
        long systemTokenEstimate,
        long systemCharacterEstimate,
        long toolTokenEstimate,
        long toolCharacterEstimate,
        long totalTokenEstimate,
        long totalCharacterEstimate,
        long tokenThreshold,
        long characterThreshold,
        int retainMessageCount,
        ConversationCompactionTrigger triggerContext) {
    public ConversationCompactionCheck {
        messages = immutableList(messages, "messages");
        triggerContext = nonNull(triggerContext, "triggerContext");
        Set<String> entryIds = new HashSet<>();
        long previousSortNo = -1;
        for (AgentMessageEntry message : messages) {
            if (!triggerContext.sessionId().equals(message.sessionId())) {
                throw new IllegalArgumentException("messages 必须属于触发上下文 sessionId");
            }
            if (!entryIds.add(message.entryId())) {
                throw new IllegalArgumentException("messages.entryId 不能重复");
            }
            if (message.sortNo() <= previousSortNo) {
                throw new IllegalArgumentException("messages 必须按 sortNo 严格递增");
            }
            previousSortNo = message.sortNo();
        }
        requireNonNegative(messageTokenEstimate, "messageTokenEstimate");
        requireNonNegative(messageCharacterEstimate, "messageCharacterEstimate");
        requireNonNegative(systemTokenEstimate, "systemTokenEstimate");
        requireNonNegative(systemCharacterEstimate, "systemCharacterEstimate");
        requireNonNegative(toolTokenEstimate, "toolTokenEstimate");
        requireNonNegative(toolCharacterEstimate, "toolCharacterEstimate");
        requireNonNegative(totalTokenEstimate, "totalTokenEstimate");
        requireNonNegative(totalCharacterEstimate, "totalCharacterEstimate");
        if (totalTokenEstimate != messageTokenEstimate + systemTokenEstimate + toolTokenEstimate) {
            throw new IllegalArgumentException("totalTokenEstimate 必须等于 message/system/tool 估算之和");
        }
        if (totalCharacterEstimate
                != messageCharacterEstimate + systemCharacterEstimate + toolCharacterEstimate) {
            throw new IllegalArgumentException(
                    "totalCharacterEstimate 必须等于 message/system/tool 估算之和");
        }
        if (tokenThreshold < 1) {
            throw new IllegalArgumentException("tokenThreshold 必须大于 0");
        }
        if (characterThreshold < 1) {
            throw new IllegalArgumentException("characterThreshold 必须大于 0");
        }
        if (retainMessageCount < 0 || retainMessageCount > messages.size()) {
            throw new IllegalArgumentException("retainMessageCount 必须位于消息窗口范围内");
        }
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " 不能小于 0");
        }
    }
}
