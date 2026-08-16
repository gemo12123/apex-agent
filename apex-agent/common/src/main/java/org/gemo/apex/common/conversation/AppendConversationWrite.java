package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageType;

public record AppendConversationWrite(AgentMessageEntry entry) implements ConversationWrite {
    public AppendConversationWrite {
        entry = nonNull(entry, "entry");
        if (entry.messageType() == MessageType.SUMMARY) {
            throw new IllegalArgumentException("SUMMARY 不能写入普通对话消息仓储");
        }
    }
}
