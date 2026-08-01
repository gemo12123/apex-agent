package org.gemo.apex.extension.repository;

import org.gemo.apex.common.conversation.ConversationCompactionCommit;
import org.gemo.apex.common.conversation.ConversationQuery;
import org.gemo.apex.common.message.AgentMessageEntry;

import java.util.List;

public interface ConversationRepository {
    /**
     * 按 entryId 幂等追加同一批消息。
     */
    void append(List<AgentMessageEntry> entries);

    List<AgentMessageEntry> load(ConversationQuery query);

    /**
     * 按 compactionId 幂等提交单个 Repository 内的压缩结果。
     */
    void compact(ConversationCompactionCommit commit);
}
