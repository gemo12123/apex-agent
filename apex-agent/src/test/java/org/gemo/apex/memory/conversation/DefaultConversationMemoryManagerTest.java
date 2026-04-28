package org.gemo.apex.memory.conversation;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.config.MemoryConfigService;
import org.gemo.apex.memory.config.MemoryProperties;
import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.model.MemoryRecallPackage;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.memory.write.MemoryLifecycleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultConversationMemoryManagerTest {

    @Mock
    private TokenEstimator tokenEstimator;

    @Mock
    private DialogueSummaryGenerator dialogueSummaryGenerator;

    @Mock
    private MemoryLifecycleManager memoryLifecycleManager;

    @Mock
    private SessionContextStore sessionContextStore;

    private DefaultConversationMemoryManager conversationMemoryManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        MemoryProperties memoryProperties = new MemoryProperties();
        memoryProperties.getCompaction().setEnabled(true);
        memoryProperties.getCompaction().setTokenThreshold(100);
        memoryProperties.getCompaction().setRetainRecentMessages(1);
        conversationMemoryManager = new DefaultConversationMemoryManager(
                tokenEstimator,
                dialogueSummaryGenerator,
                new MemoryConfigService(memoryProperties),
                memoryLifecycleManager,
                sessionContextStore);
    }

    @Test
    void compactIfNeeded_ShouldPersistTailAndRewriteSummaryInStore() {
        SuperAgentContext context = new SuperAgentContext();
        SystemMessage latestSummary = new SystemMessage("old-summary");
        UserMessage firstUserMessage = new UserMessage("u1");
        AssistantMessage firstAssistantMessage = new AssistantMessage("a1");
        UserMessage retainedMessage = new UserMessage("u2");

        context.setSessionId("session-1");
        context.setTurnNo(2);
        context.setLatestCompressedMessage(latestSummary);
        context.setLatestCompressedSortNo(10L);
        context.setTurnStartSortNo(10L);
        context.setDialogueMessages(new ArrayList<>(List.of(firstUserMessage, firstAssistantMessage, retainedMessage)));
        context.setPersistedDialogueMessageIndex(2);

        when(tokenEstimator.estimate(any())).thenReturn(200);
        when(sessionContextStore.countUncompactedMessagesBeforeTurn("session-1", 2)).thenReturn(2);
        when(dialogueSummaryGenerator.generateSummary(eq(latestSummary), eq(List.of(firstUserMessage, firstAssistantMessage))))
                .thenReturn("new-summary");

        conversationMemoryManager.compactIfNeeded(context);

        assertInstanceOf(SystemMessage.class, context.getLatestCompressedMessage());
        assertEquals("new-summary", context.getLatestCompressedMessage().getText());
        assertEquals(12L, context.getLatestCompressedSortNo());
        assertEquals(12L, context.getTurnStartSortNo());
        assertEquals(1, context.getDialogueMessages().size());
        assertEquals("u2", context.getDialogueMessages().getFirst().getText());
        assertEquals(1, context.getPersistedDialogueMessageIndex());
        assertEquals(14L, context.getNextMessageSortNo());
        verify(sessionContextStore).appendDialogueMessages("session-1", 2, 12L, List.of(retainedMessage));
        verify(sessionContextStore).compactDialogue("session-1", context.getLatestCompressedMessage(), 12L, 2);
        verify(dialogueSummaryGenerator).generateSummary(latestSummary, List.of(firstUserMessage, firstAssistantMessage));
    }

    @Test
    void refreshFixedMessagesShouldRenderProfileAndExperienceOnly() {
        SuperAgentContext context = new SuperAgentContext();
        context.setUserId("user-123");
        context.setMemoryRecallPackage(recallPackage());

        conversationMemoryManager.refreshFixedMessages(context, "stage-system-prompt");

        assertEquals(3, context.getFixedMessages().size());
        assertInstanceOf(SystemMessage.class, context.getFixedMessages().get(0));
        assertEquals("stage-system-prompt", context.getFixedMessages().get(0).getText());
        assertInstanceOf(UserMessage.class, context.getFixedMessages().get(1));
        assertTrue(context.getFixedMessages().get(1).getText().contains("user-123"));
        assertInstanceOf(UserMessage.class, context.getFixedMessages().get(2));
        assertTrue(context.getFixedMessages().get(2).getText().contains("用户画像记忆"));
        assertTrue(context.getFixedMessages().get(2).getText().contains("智能体经验记忆"));
        assertFalse(context.getFixedMessages().get(2).getText().contains("用户执行历史记忆"));
    }

    @Test
    void buildModelMessagesShouldKeepFixedSummaryDialogueOrder() {
        SuperAgentContext context = new SuperAgentContext();
        context.setFixedMessages(new ArrayList<>(List.of(
                new SystemMessage("fixed-1"),
                new UserMessage("recall-1"))));
        context.setLatestCompressedMessage(new SystemMessage("summary-1"));
        context.setDialogueMessages(new ArrayList<>(List.of(
                new UserMessage("user-1"),
                new AssistantMessage("assistant-1"))));

        List<Message> messages = conversationMemoryManager.buildModelMessages(context);

        assertEquals(List.of("fixed-1", "recall-1", "summary-1", "user-1", "assistant-1"),
                messages.stream().map(Message::getText).toList());
    }

    private MemoryRecallPackage recallPackage() {
        MemoryRecallPackage recallPackage = new MemoryRecallPackage();
        recallPackage.setProfileItems(List.of(memoryItem("画像", "偏好咖啡")));
        recallPackage.setExperienceItems(List.of(memoryItem("经验", "优先展示澄清选项")));
        return recallPackage;
    }

    private MemoryItem memoryItem(String title, String content) {
        MemoryItem item = new MemoryItem();
        item.setTitle(title);
        item.setContent(content);
        return item;
    }
}
