package org.gemo.apex.extension.conversation;

import org.gemo.apex.common.conversation.ConversationWindow;
import org.gemo.apex.common.conversation.ConversationWindowRequest;

public interface ConversationWindowManager {
    /** 准备业务模型窗口，不执行压缩或提交 compacted 状态。 */
    ConversationWindow prepare(ConversationWindowRequest request);
}
