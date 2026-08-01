package org.gemo.apex.common.conversation;

import org.gemo.apex.common.message.AgentMessageEntry;

import java.util.List;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.required;

public record ConversationWindow(String sessionId, List<AgentMessageEntry> messages,
                                 Long firstSortNo, Long lastSortNo) {
    public ConversationWindow {
        sessionId = required(sessionId, "sessionId");
        messages = immutableList(messages, "messages");
        for (AgentMessageEntry message : messages) {
            if (!sessionId.equals(message.sessionId())) {
                throw new IllegalArgumentException("窗口消息必须属于同一 sessionId");
            }
        }
        if (messages.isEmpty()) {
            if (firstSortNo != null || lastSortNo != null) {
                throw new IllegalArgumentException("空窗口不能包含 sortNo 边界");
            }
        } else {
            long actualFirst = messages.getFirst().sortNo();
            long actualLast = messages.getLast().sortNo();
            if (firstSortNo == null || lastSortNo == null
                    || firstSortNo != actualFirst || lastSortNo != actualLast) {
                throw new IllegalArgumentException("窗口 sortNo 边界必须与消息一致");
            }
            for (int index = 1; index < messages.size(); index++) {
                if (messages.get(index - 1).sortNo() >= messages.get(index).sortNo()) {
                    throw new IllegalArgumentException("窗口消息必须按 sortNo 严格递增");
                }
            }
        }
    }
}
