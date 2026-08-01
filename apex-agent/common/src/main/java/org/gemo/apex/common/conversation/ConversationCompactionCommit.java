package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.required;

public record ConversationCompactionCommit(String sessionId, String compactionId,
                                           long sourceStartSortNo, long sourceEndSortNo,
                                           String summary) {
    public ConversationCompactionCommit {
        sessionId = required(sessionId, "sessionId");
        compactionId = required(compactionId, "compactionId");
        if (sourceStartSortNo < 0 || sourceEndSortNo < sourceStartSortNo) {
            throw new IllegalArgumentException("压缩来源边界非法");
        }
        summary = required(summary, "summary");
    }
}
