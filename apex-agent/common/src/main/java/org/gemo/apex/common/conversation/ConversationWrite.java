package org.gemo.apex.common.conversation;

public sealed interface ConversationWrite
        permits AppendConversationWrite,
                ReplaceConversationWrite,
                RemoveConversationWrite,
                CompactConversationWrite {}
