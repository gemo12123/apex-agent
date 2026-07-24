package org.gemo.apex.memory.session;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.skills.learning.model.SkillSessionMessage;
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

    void appendDialogueMessages(String sessionId, Long turnNo, Long baseSortNo, List<Message> messages);

    default void appendDialogueMessages(String sessionId, Integer turnNo, Long baseSortNo, List<Message> messages) {
        appendDialogueMessages(sessionId, turnNo != null ? turnNo.longValue() : null, baseSortNo, messages);
    }

    List<Message> loadAllRawDialogueMessages(String sessionId);

    List<SkillSessionMessage> loadSkillSessionMessages(String sessionId);

    int countUncompactedMessagesBeforeTurn(String sessionId, Long turnNo);

    default int countUncompactedMessagesBeforeTurn(String sessionId, Integer turnNo) {
        return countUncompactedMessagesBeforeTurn(sessionId, turnNo != null ? turnNo.longValue() : null);
    }

    void compactDialogue(String sessionId, Message summaryMessage, Long compactedToSortNo, Long turnNo);

    default void compactDialogue(String sessionId, Message summaryMessage, Long compactedToSortNo, Integer turnNo) {
        compactDialogue(sessionId, summaryMessage, compactedToSortNo,
                turnNo != null ? turnNo.longValue() : null);
    }

    /**
     * 删除上下文。
     */
    void delete(String sessionId);
}
