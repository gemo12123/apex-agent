package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.required;

import java.util.List;

public record ConversationWriteBatch(String sessionId, List<ConversationWrite> writes) {
    public ConversationWriteBatch {
        sessionId = required(sessionId, "sessionId");
        writes = immutableList(writes, "writes");
        if (writes.isEmpty()) {
            throw new IllegalArgumentException("ConversationWriteBatch.writes 不能为空");
        }
        for (ConversationWrite write : writes) {
            if (write instanceof AppendConversationWrite append
                    && !sessionId.equals(append.entry().sessionId())) {
                throw new IllegalArgumentException("追加消息必须属于批次 Session");
            }
            if (write instanceof CompactConversationWrite compact
                    && !sessionId.equals(compact.commit().sessionId())) {
                throw new IllegalArgumentException("压缩提交必须属于批次 Session");
            }
        }
    }
}
