package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record CompactConversationWrite(ConversationCompactionCommit commit)
        implements ConversationWrite {
    public CompactConversationWrite {
        commit = nonNull(commit, "commit");
    }
}
