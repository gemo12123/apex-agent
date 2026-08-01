package org.gemo.apex.common.conversation;

public record ConversationCompactionCheck(boolean shouldCompact, long sourceStartSortNo,
                                          long sourceEndSortNo) {
    public ConversationCompactionCheck {
        if (sourceStartSortNo < 0 || sourceEndSortNo < sourceStartSortNo) {
            throw new IllegalArgumentException("压缩检查边界非法");
        }
    }
}
