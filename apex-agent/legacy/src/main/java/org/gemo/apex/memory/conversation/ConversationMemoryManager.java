package org.gemo.apex.memory.conversation;

import org.gemo.apex.context.SuperAgentContext;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 会话消息管理器。
 */
public interface ConversationMemoryManager {

    /**
     * 刷新固定消息。
     */
    void refreshFixedMessages(SuperAgentContext context, String stageSystemPrompt);

    /**
     * 追加对话消息。
     */
    void appendDialogueMessage(SuperAgentContext context, Message message);

    /**
     * 如有必要执行压缩。
     */
    void compactIfNeeded(SuperAgentContext context);

    /**
     * 构建送模消息。
     */
    List<Message> buildModelMessages(SuperAgentContext context);
}
