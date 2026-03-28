package org.gemo.apex.memory.conversation;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.config.MemoryConfigService;
import org.gemo.apex.memory.config.MemoryProperties;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.memory.write.MemoryLifecycleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
}
