package org.gemo.apex.extension.conversation;

import org.gemo.apex.common.conversation.ConversationCompactionCheck;

public interface ConversationCompactionPolicy {
    /** 仅依据完整检查对象判断，不自行读取 SessionRepository。 */
    boolean shouldCompact(ConversationCompactionCheck check);
}
