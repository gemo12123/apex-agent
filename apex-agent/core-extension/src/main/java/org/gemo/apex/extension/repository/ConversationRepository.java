package org.gemo.apex.extension.repository;

import java.util.List;
import org.gemo.apex.common.conversation.ConversationCompactionCommit;
import org.gemo.apex.common.conversation.ConversationHistory;
import org.gemo.apex.common.conversation.ConversationQuery;
import org.gemo.apex.common.message.AgentMessageEntry;

public interface ConversationRepository {
    /** 按 entryId 幂等追加同一批消息。 */
    void append(List<AgentMessageEntry> entries);

    ConversationHistory load(ConversationQuery query);

    /** 按 compactionId 幂等提交单个 Repository 内的压缩结果。 */
    void compact(ConversationCompactionCommit commit);
}
