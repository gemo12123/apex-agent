package org.gemo.apex.extension.conversation;

import org.gemo.apex.common.conversation.ConversationCompactionRequest;
import org.gemo.apex.common.conversation.ConversationCompactionResult;

public interface ConversationCompactor {
    /** 使用独立摘要入口生成结果，不得递归进入业务 Agent 主循环。 */
    ConversationCompactionResult compact(ConversationCompactionRequest request);
}
