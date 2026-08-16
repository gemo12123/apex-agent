package org.gemo.apex.extension.repository;

import org.gemo.apex.common.conversation.ConversationHistory;
import org.gemo.apex.common.conversation.ConversationQuery;
import org.gemo.apex.common.conversation.ConversationWriteBatch;

public interface ConversationRepository {
    /** 在单个 Repository 事务中按顺序提交对话写操作。 */
    void commit(ConversationWriteBatch batch);

    ConversationHistory load(ConversationQuery query);
}
