package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import java.time.Instant;

/** 持久化的累计对话摘要及其替代的原始消息范围。 */
public record ConversationSummary(
        String compactionId,
        String content,
        long sourceStartSortNo,
        long sourceEndSortNo,
        long sourceTurnNo,
        Instant updatedTime) {
    public ConversationSummary {
        compactionId = required(compactionId, "compactionId");
        content = required(content, "content");
        if (sourceStartSortNo < 0 || sourceEndSortNo < sourceStartSortNo) {
            throw new IllegalArgumentException("摘要来源边界非法");
        }
        if (sourceTurnNo < 0) {
            throw new IllegalArgumentException("sourceTurnNo 不能小于 0");
        }
        updatedTime = nonNull(updatedTime, "updatedTime");
    }
}
