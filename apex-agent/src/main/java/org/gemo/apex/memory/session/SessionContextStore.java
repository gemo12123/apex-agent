package org.gemo.apex.memory.session;

import org.gemo.apex.context.SuperAgentContext;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Optional;

/**
 * 会话上下文存储接口。
 */
public interface SessionContextStore {

    /**
     * 按会话标识加载上下文。
     */
    Optional<SuperAgentContext> load(String sessionId);

    /**
     * 保存上下文。
     */
    void save(SuperAgentContext context);

    void appendDialogueMessages(String sessionId, Integer turnNo, Long baseSortNo, List<Message> messages);

    List<Message> loadAllRawDialogueMessages(String sessionId);

    int countUncompactedMessagesBeforeTurn(String sessionId, Integer turnNo);

    void compactDialogue(String sessionId, Message summaryMessage, Long compactedToSortNo, Integer turnNo);

    /**
     * 删除上下文。
     */
    void delete(String sessionId);
}
