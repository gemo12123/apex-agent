package org.gemo.apex.memory.extract;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.config.MemoryConfigService;
import org.gemo.apex.memory.model.ExecutionTimeScope;
import org.gemo.apex.memory.model.MemoryCategory;
import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.model.MemoryType;
import org.gemo.apex.util.JacksonUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 记忆候选项提取服务。
 */
@Slf4j
@Service
public class MemoryExtractionService {

    private final ChatClient chatClient;
    private final MemoryConfigService memoryConfigService;
    private final PromptTemplateLoader promptTemplateLoader;

    public MemoryExtractionService(ChatClient chatClient,
            MemoryConfigService memoryConfigService,
            PromptTemplateLoader promptTemplateLoader) {
        this.chatClient = chatClient;
        this.memoryConfigService = memoryConfigService;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    public List<MemoryItem> extractProfileCandidates(SuperAgentContext context) {
        return extractProfileCandidates(context, mergeConversation(context));
    }

    public List<MemoryItem> extractProfileCandidates(SuperAgentContext context, List<Message> rawMessages) {
        if (!memoryConfigService.isProfileEnabled()) {
            return List.of();
        }
        return invokePromptExtraction(context, rawMessages, MemoryType.LONG_TERM,
                memoryConfigService.getProperties().getExtraction().getLongTermPrompt());
    }

    public List<MemoryItem> extractExecutionHistoryCandidates(SuperAgentContext context) {
        return extractExecutionHistoryCandidates(context, mergeConversation(context));
    }

    public List<MemoryItem> extractExecutionHistoryCandidates(SuperAgentContext context, List<Message> rawMessages) {
        if (!memoryConfigService.isExecutionHistoryEnabled()) {
            return List.of();
        }
        List<MemoryItem> items = invokePromptExtraction(context, rawMessages, MemoryType.EXECUTION_HISTORY,
                memoryConfigService.getProperties().getExtraction().getExecutionHistoryPrompt());
        for (MemoryItem item : items) {
            if (item.getTimeScope() == null) {
                item.setTimeScope(ExecutionTimeScope.RECENT);
            }
        }
        return items;
    }

    public List<MemoryItem> extractExperienceCandidates(SuperAgentContext context) {
        return extractExperienceCandidates(context, mergeConversation(context));
    }

    public List<MemoryItem> extractExperienceCandidates(SuperAgentContext context, List<Message> rawMessages) {
        if (!memoryConfigService.isExperienceEnabled()) {
            return List.of();
        }
        return invokePromptExtraction(context, rawMessages, MemoryType.AGENT_EXPERIENCE,
                memoryConfigService.getProperties().getExtraction().getExperiencePrompt());
    }

    public List<MemoryItem> extractCompactionCandidates(SuperAgentContext context, List<Message> oldDialogueMessages) {
        if (!memoryConfigService.isProfileEnabled() && !memoryConfigService.isExecutionHistoryEnabled()) {
            return List.of();
        }
        return invokePromptExtraction(context, oldDialogueMessages, MemoryType.EXECUTION_HISTORY,
                memoryConfigService.getProperties().getExtraction().getExecutionHistoryPrompt());
    }

    private List<Message> mergeConversation(SuperAgentContext context) {
        List<Message> messages = new ArrayList<>();
        if (context.getLatestCompressedMessage() != null) {
            messages.add(context.getLatestCompressedMessage());
        }
        messages.addAll(context.getDialogueMessages());
        return messages;
    }

    private List<MemoryItem> invokePromptExtraction(SuperAgentContext context, List<Message> messages, MemoryType defaultType,
            String promptLocation) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        String conversationText = renderConversation(messages);
        String prompt = promptTemplateLoader.load(promptLocation).replace("{conversation}", conversationText);
        try {
            String content = chatClient.prompt(prompt).call().content();
            List<ExtractedMemoryPayload> payloads = JacksonUtils.fromJson(content,
                    new TypeReference<List<ExtractedMemoryPayload>>() {
                    });
            if (payloads == null || payloads.isEmpty()) {
                return fallbackExtract(context, messages, defaultType);
            }
            return payloads.stream()
                    .filter(payload -> payload.getConfidence() == null
                            || payload.getConfidence() >= memoryConfigService.getProperties().getExtraction().getMinConfidence())
                    .map(payload -> toMemoryItem(context, payload, defaultType))
                    .toList();
        } catch (Exception ex) {
            log.warn("会话 [{}] 记忆抽取失败，降级走规则抽取: {}", context.getSessionId(), ex.getMessage());
            return fallbackExtract(context, messages, defaultType);
        }
    }

    private List<MemoryItem> fallbackExtract(SuperAgentContext context, List<Message> messages, MemoryType defaultType) {
        String latestUserText = messages.stream()
                .filter(message -> message != null
                        && message.getMessageType() == org.springframework.ai.chat.messages.MessageType.USER)
                .map(Message::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((first, second) -> second)
                .orElse(null);
        if (latestUserText == null || latestUserText.isBlank()) {
            return List.of();
        }

        MemoryItem item = new MemoryItem();
        item.setId(IdUtil.simpleUUID());
        item.setUserId(context.getUserId());
        item.setAgentKey(context.getAgentKey());
        item.setType(defaultType);
        item.setSourceSessionId(context.getSessionId());
        item.setObservedTime(LocalDateTime.now());
        item.setConfidence(0.7D);
        item.setImportance(5);

        if (defaultType == MemoryType.EXECUTION_HISTORY) {
            item.setTopicKey(normalizeKey(latestUserText));
            item.setTitle("近期执行线索");
            item.setContent(latestUserText);
            item.setTimeScope(ExecutionTimeScope.RECENT);
            item.setTurnNo(context.getTurnNo());
            item.setVersionNo(context.getTurnNo() != null ? context.getTurnNo().longValue() : null);
        } else if (defaultType == MemoryType.AGENT_EXPERIENCE) {
            item.setTopicKey(normalizeKey(latestUserText));
            item.setMemoryKey(normalizeKey(latestUserText));
            item.setTitle("用户交互偏好线索");
            item.setContent(latestUserText);
        } else {
            item.setType(matchProfileType(latestUserText));
            item.setMemoryCategory(item.getType() == MemoryType.FACT ? MemoryCategory.FACT : MemoryCategory.LONG_TERM);
            item.setMemoryKey(normalizeKey(latestUserText));
            item.setTitle(item.getType() == MemoryType.FACT ? "用户事实线索" : "用户长期偏好线索");
            item.setContent(latestUserText);
        }
        return List.of(item);
    }

    private MemoryItem toMemoryItem(SuperAgentContext context, ExtractedMemoryPayload payload, MemoryType defaultType) {
        MemoryItem item = new MemoryItem();
        item.setId(payload.getId() != null ? payload.getId() : IdUtil.simpleUUID());
        item.setUserId(context.getUserId());
        item.setAgentKey(context.getAgentKey());
        item.setType(payload.getMemoryType() != null ? payload.getMemoryType() : defaultType);
        item.setMemoryCategory(payload.getMemoryCategory());
        item.setMemoryKey(payload.getMemoryKey() != null ? payload.getMemoryKey() : normalizeKey(payload.getTitle()));
        item.setTopicKey(payload.getTopicKey() != null ? payload.getTopicKey() : normalizeKey(payload.getTitle()));
        item.setTitle(payload.getTitle());
        item.setContent(payload.getContent());
        item.setStructuredPayload(payload.getStructuredPayload());
        item.setConfidence(payload.getConfidence() != null ? payload.getConfidence() : 0.7D);
        item.setImportance(payload.getImportance() != null ? payload.getImportance() : 5);
        item.setTimeScope(payload.getTimeScope());
        item.setSourceSessionId(context.getSessionId());
        item.setObservedTime(LocalDateTime.now());
        item.setTurnNo(context.getTurnNo());
        item.setVersionNo(context.getTurnNo() != null ? context.getTurnNo().longValue() : null);
        if (item.getType() == MemoryType.FACT || item.getType() == MemoryType.LONG_TERM) {
            item.setMemoryCategory(item.getType() == MemoryType.FACT ? MemoryCategory.FACT : MemoryCategory.LONG_TERM);
        }
        return item;
    }

    private MemoryType matchProfileType(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.contains("璐熻矗") || normalized.contains("椤圭洰") || normalized.contains("浣跨敤")) {
            return MemoryType.FACT;
        }
        return MemoryType.LONG_TERM;
    }

    private String renderConversation(List<Message> messages) {
        StringBuilder builder = new StringBuilder();
        for (Message message : messages) {
            if (message == null || message.getText() == null) {
                continue;
            }
            builder.append("[").append(message.getMessageType().name()).append("] ")
                    .append(message.getText()).append("\n");
        }
        return builder.toString();
    }

    private String normalizeKey(String text) {
        if (text == null || text.isBlank()) {
            return "memory";
        }
        String normalized = text.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+", "_");
        if (normalized.length() > 48) {
            normalized = normalized.substring(0, 48);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    @Data
    private static class ExtractedMemoryPayload {
        private String id;
        private MemoryType memoryType;
        private MemoryCategory memoryCategory;
        private String memoryKey;
        private String topicKey;
        private String title;
        private String content;
        private String structuredPayload;
        private Double confidence;
        private Integer importance;
        private ExecutionTimeScope timeScope;
    }
}
