package org.gemo.apex.memory.conversation;

import org.gemo.apex.memory.extract.PromptTemplateLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于大模型的对话摘要生成器。
 */
@Component
public class LlmDialogueSummaryGenerator implements DialogueSummaryGenerator {

    private final ChatClient chatClient;
    private final PromptTemplateLoader promptTemplateLoader;

    public LlmDialogueSummaryGenerator(ChatClient chatClient, PromptTemplateLoader promptTemplateLoader) {
        this.chatClient = chatClient;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    @Override
    public String generateSummary(Message latestCompressedMessage, List<Message> messagesToCompress) {
        StringBuilder conversation = new StringBuilder();
        if (latestCompressedMessage != null && latestCompressedMessage.getText() != null) {
            conversation.append("【已有摘要】\n").append(latestCompressedMessage.getText()).append("\n\n");
        }
        if (messagesToCompress != null) {
            for (Message message : messagesToCompress) {
                if (message == null || message.getText() == null) {
                    continue;
                }
                conversation.append("【").append(message.getMessageType().name()).append("】\n")
                        .append(message.getText()).append("\n\n");
            }
        }
        String prompt = promptTemplateLoader.load("classpath:prompts/memory/dialogue-summary.st")
                .replace("{conversation}", conversation.toString());
        try {
            return chatClient.prompt(prompt).call().content();
        } catch (Exception ex) {
            return latestCompressedMessage != null ? latestCompressedMessage.getText() : conversation.toString();
        }
    }
}
