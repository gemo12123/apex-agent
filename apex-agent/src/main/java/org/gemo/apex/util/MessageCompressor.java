package org.gemo.apex.util;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.context.SuperAgentContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SuperAgent 的消息历史压缩工具。
 * 作用：应对会话不断堆积、超出模型 Token 负荷的问题，定期取走前面过期的记录进行压缩并保存摘要。
 */
@Slf4j
@Component
public class MessageCompressor {

    private final ChatClient chatClient;

    // 假设保留最近的N条非系统级消息，前面的用于压缩
    private static final int RETAIN_RECENT_COUNT = 15;
    // 触发压缩的历史记录数阈值
    private static final int COMPRESS_THRESHOLD = 30;

    public MessageCompressor(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 根据当前会话执行压缩。如果未达阈值，直接返回原列表。
     */
    public List<Message> compressHistoryIfNeeded(SuperAgentContext context) {
        List<Message> originalMessages = context.getMessages();

        if (originalMessages.size() <= COMPRESS_THRESHOLD) {
            return originalMessages;
        }

        log.info("Session {} message length {} exceeded threshold, triggering memory compression...",
                context.getSessionId(), originalMessages.size());

        List<Message> toBeCompressed = new ArrayList<>();
        List<Message> retainMessages = new ArrayList<>();
        List<Message> systemMessages = new ArrayList<>();

        int messageTotal = originalMessages.size();
        for (int i = 0; i < messageTotal; i++) {
            Message msg = originalMessages.get(i);
            if (msg instanceof SystemMessage) {
                // System 级别的指令不压缩
                systemMessages.add(msg);
            } else if (i < messageTotal - RETAIN_RECENT_COUNT) {
                // 早期的信息推入压缩流
                toBeCompressed.add(msg);
            } else {
                retainMessages.add(msg);
            }
        }

        if (toBeCompressed.isEmpty()) {
            return originalMessages;
        }

        String compressedSummary = callModelForSummary(toBeCompressed);

        AssistantMessage summaryMsg = new AssistantMessage("[Historical memory abstract summary] " + compressedSummary);

        List<Message> newContextMsg = new ArrayList<>(systemMessages);
        newContextMsg.add(summaryMsg);
        newContextMsg.addAll(retainMessages);

        context.setMessages(newContextMsg);

        log.info("Compression done, message sum dropped from {} to {}", originalMessages.size(), newContextMsg.size());

        return newContextMsg;
    }

    private String callModelForSummary(List<Message> toBeCompressed) {
        StringBuilder tx = new StringBuilder(
                "Please extract the most core content (especially completed tasks, decisions and context) based on the following multi-turn conversation log, compress them into a short abstract summary.\n\nConversation Log:\n");
        for (Message msg : toBeCompressed) {
            tx.append(msg.getMessageType()).append(": ").append(msg.getText()).append("\n");
        }

        return chatClient.prompt(tx.toString()).call().content();
    }
}
