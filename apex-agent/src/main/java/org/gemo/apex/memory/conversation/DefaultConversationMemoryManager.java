package org.gemo.apex.memory.conversation;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.config.MemoryConfigService;
import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.model.MemoryRecallPackage;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.memory.write.MemoryLifecycleManager;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认会话消息管理器。
 */
@Slf4j
@Component
public class DefaultConversationMemoryManager implements ConversationMemoryManager {

    private final TokenEstimator tokenEstimator;
    private final DialogueSummaryGenerator dialogueSummaryGenerator;
    private final MemoryConfigService memoryConfigService;
    private final MemoryLifecycleManager memoryLifecycleManager;
    private final SessionContextStore sessionContextStore;

    public DefaultConversationMemoryManager(TokenEstimator tokenEstimator,
            DialogueSummaryGenerator dialogueSummaryGenerator,
            MemoryConfigService memoryConfigService,
            MemoryLifecycleManager memoryLifecycleManager,
            SessionContextStore sessionContextStore) {
        this.tokenEstimator = tokenEstimator;
        this.dialogueSummaryGenerator = dialogueSummaryGenerator;
        this.memoryConfigService = memoryConfigService;
        this.memoryLifecycleManager = memoryLifecycleManager;
        this.sessionContextStore = sessionContextStore;
    }

    @Override
    public void refreshFixedMessages(SuperAgentContext context, String stageSystemPrompt) {
        List<Message> fixedMessages = new ArrayList<>();
        fixedMessages.add(new SystemMessage(stageSystemPrompt));

        String recallText = renderRecallText(context.getMemoryRecallPackage());
        if (!recallText.isBlank()) {
            fixedMessages.add(new SystemMessage(recallText));
        }
        context.setFixedMessages(fixedMessages);
    }

    @Override
    public void appendDialogueMessage(SuperAgentContext context, Message message) {
        context.addMessage(message);
        context.setNextMessageSortNo(context.getTurnStartSortNo() + context.getPersistedDialogueMessageIndex() + 1L);
    }

    @Override
    public void compactIfNeeded(SuperAgentContext context) {
        if (!memoryConfigService.isCompactionEnabled()) {
            return;
        }

        persistDialogueMessages(context);

        List<Message> nonFixedMessages = new ArrayList<>();
        if (context.getLatestCompressedMessage() != null) {
            nonFixedMessages.add(context.getLatestCompressedMessage());
        }
        nonFixedMessages.addAll(context.getDialogueMessages());

        if (tokenEstimator.estimate(nonFixedMessages)
                <= memoryConfigService.getProperties().getCompaction().getTokenThreshold()) {
            return;
        }

        int retainCount = memoryConfigService.getProperties().getCompaction().getRetainRecentMessages();
        int dialogueSize = context.getDialogueMessages().size();
        int retainedCount = Math.min(retainCount, dialogueSize);
        int eligibleCount = sessionContextStore.countUncompactedMessagesBeforeTurn(context.getSessionId(),
                context.getTurnNo());
        int candidateCount = Math.min(eligibleCount, Math.max(dialogueSize - retainedCount, 0));
        if (candidateCount <= 0) {
            return;
        }

        List<Message> oldDialogueMessages = new ArrayList<>(context.getDialogueMessages().subList(0, candidateCount));
        String summary = dialogueSummaryGenerator.generateSummary(context.getLatestCompressedMessage(),
                oldDialogueMessages);
        SystemMessage summaryMessage = new SystemMessage(summary);
        long compactedToSortNo = context.getTurnStartSortNo() + candidateCount;

        sessionContextStore.compactDialogue(context.getSessionId(), summaryMessage, compactedToSortNo, context.getTurnNo());

        context.setLatestCompressedMessage(summaryMessage);
        context.setLatestCompressedSortNo(compactedToSortNo);
        context.setDialogueMessages(new ArrayList<>(context.getDialogueMessages().subList(candidateCount, dialogueSize)));
        context.setTurnStartSortNo(compactedToSortNo);
        context.setPersistedDialogueMessageIndex(context.getDialogueMessages().size());
        context.setNextMessageSortNo(context.getTurnStartSortNo() + context.getPersistedDialogueMessageIndex() + 1L);
        log.info("会话 [{}] 已完成一次消息压缩，当前摘要边界为 {}", context.getSessionId(),
                context.getLatestCompressedSortNo());
    }

    @Override
    public List<Message> buildModelMessages(SuperAgentContext context) {
        List<Message> messages = new ArrayList<>(context.getFixedMessages());
        if (context.getLatestCompressedMessage() != null) {
            messages.add(context.getLatestCompressedMessage());
        }
        messages.addAll(context.getDialogueMessages());
        return messages;
    }

    private void persistDialogueMessages(SuperAgentContext context) {
        int persistedIndex = context.getPersistedDialogueMessageIndex() != null
                ? context.getPersistedDialogueMessageIndex()
                : 0;
        if (persistedIndex >= context.getDialogueMessages().size()) {
            return;
        }
        List<Message> messagesToPersist = new ArrayList<>(
                context.getDialogueMessages().subList(persistedIndex, context.getDialogueMessages().size()));
        long baseSortNo = context.getTurnStartSortNo() + persistedIndex;
        sessionContextStore.appendDialogueMessages(context.getSessionId(), context.getTurnNo(), baseSortNo,
                messagesToPersist);
        context.setPersistedDialogueMessageIndex(context.getDialogueMessages().size());
        context.setNextMessageSortNo(context.getTurnStartSortNo() + context.getPersistedDialogueMessageIndex() + 1L);
    }

    private String renderRecallText(MemoryRecallPackage recallPackage) {
        if (recallPackage == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendSection(builder, "用户画像记忆", recallPackage.getProfileItems());
        appendSection(builder, "用户执行历史记忆", recallPackage.getExecutionHistoryItems());
        appendSection(builder, "智能体经验记忆", recallPackage.getExperienceItems());
        return builder.toString();
    }

    private void appendSection(StringBuilder builder, String title, List<MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(title).append(":\n");
        for (MemoryItem item : items) {
            builder.append("- ").append(item.getTitle() != null ? item.getTitle() : "未命名记忆")
                    .append(": ")
                    .append(item.getContent())
                    .append("\n");
        }
    }
}
