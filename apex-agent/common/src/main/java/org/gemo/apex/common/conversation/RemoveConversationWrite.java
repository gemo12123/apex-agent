package org.gemo.apex.common.conversation;

import static org.gemo.apex.common.support.DomainValues.required;

public record RemoveConversationWrite(String targetEntryId) implements ConversationWrite {
    public RemoveConversationWrite {
        targetEntryId = required(targetEntryId, "targetEntryId");
    }
}
