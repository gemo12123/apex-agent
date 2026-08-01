package org.gemo.apex.memory.conversation;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于字符长度的保守 token 估算器。
 */
@Component
public class SimpleTokenEstimator implements TokenEstimator {

    @Override
    public int estimate(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return messages.stream()
                .map(Message::getText)
                .filter(text -> text != null && !text.isEmpty())
                .mapToInt(text -> Math.max(1, text.length() / 4))
                .sum();
    }
}
