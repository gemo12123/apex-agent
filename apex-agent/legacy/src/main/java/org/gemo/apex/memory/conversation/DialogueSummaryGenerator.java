package org.gemo.apex.memory.conversation;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 对话摘要生成器。
 */
public interface DialogueSummaryGenerator {

    /**
     * 根据旧摘要和待压缩消息生成新摘要。
     */
    String generateSummary(Message latestCompressedMessage, List<Message> messagesToCompress);
}
