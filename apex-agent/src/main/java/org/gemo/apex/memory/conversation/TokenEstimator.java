package org.gemo.apex.memory.conversation;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Token 估算接口。
 */
public interface TokenEstimator {

    /**
     * 估算消息列表 token 数。
     */
    int estimate(List<Message> messages);
}
