package org.gemo.apex.memory.write;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.config.MemoryConfigService;
import org.gemo.apex.memory.config.MemoryProperties;
import org.gemo.apex.memory.extract.MemoryExtractionService;
import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.session.SessionContextStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryLifecycleManagerTest {

    @Mock
    private MemoryExtractionService memoryExtractionService;

    @Mock
    private MemoryWriteService memoryWriteService;

    @Mock
    private SessionContextStore sessionContextStore;

    @Mock
    private ThreadPoolTaskScheduler taskScheduler;

    private MemoryLifecycleManager memoryLifecycleManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        MemoryProperties memoryProperties = new MemoryProperties();
        memoryProperties.getExtraction().setAsyncEnabled(false);
        memoryLifecycleManager = new MemoryLifecycleManager(
                new MemoryConfigService(memoryProperties),
                memoryExtractionService,
                memoryWriteService,
                sessionContextStore,
                taskScheduler);
    }

    @Test
    void onTurnCompletedShouldPrependPersistedFixedMessagesForExtraction() {
        SuperAgentContext runtimeContext = new SuperAgentContext();
        runtimeContext.setSessionId("session-1");
        runtimeContext.setUserId("user-1");
        runtimeContext.setAgentKey("agent-1");
        runtimeContext.setTurnNo(3);
        runtimeContext.setLastActiveTime(LocalDateTime.of(2026, 3, 17, 10, 30));
        runtimeContext.setFixedMessages(new ArrayList<>(List.of(
                new SystemMessage("stage-system-prompt"),
                new UserMessage("Current user id: user-1"))));

        List<Message> rawMessages = List.of(
                new UserMessage("user-1"),
                new AssistantMessage("assistant-1"),
                new UserMessage("user-2"));
        List<Message> extractionMessages = List.of(
                new SystemMessage("stage-system-prompt"),
                new UserMessage("Current user id: user-1"),
                new UserMessage("user-1"),
                new AssistantMessage("assistant-1"),
                new UserMessage("user-2"));

        MemoryItem executionItem = new MemoryItem();
        executionItem.setId("execution");
        MemoryItem experienceItem = new MemoryItem();
        experienceItem.setId("experience");
        MemoryItem profileItem = new MemoryItem();
        profileItem.setId("profile");

        List<MemoryItem> executionCandidates = List.of(executionItem);
        List<MemoryItem> experienceCandidates = List.of(experienceItem);
        List<MemoryItem> profileCandidates = List.of(profileItem);

        when(sessionContextStore.load("session-1")).thenReturn(Optional.of(runtimeContext));
        when(sessionContextStore.loadAllRawDialogueMessages("session-1")).thenReturn(rawMessages);
        when(memoryExtractionService.extractExecutionHistoryCandidates(runtimeContext, extractionMessages))
                .thenReturn(executionCandidates);
        when(memoryExtractionService.extractExperienceCandidates(runtimeContext, extractionMessages))
                .thenReturn(experienceCandidates);
        when(memoryExtractionService.extractProfileCandidates(runtimeContext, extractionMessages))
                .thenReturn(profileCandidates);
        when(taskScheduler.schedule(any(Runnable.class), any(java.util.Date.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            return future;
        });

        memoryLifecycleManager.onTurnCompleted(runtimeContext);

        verify(sessionContextStore, times(2)).load("session-1");
        verify(sessionContextStore, times(2)).loadAllRawDialogueMessages("session-1");
        verify(memoryExtractionService).extractExecutionHistoryCandidates(runtimeContext, extractionMessages);
        verify(memoryExtractionService).extractExperienceCandidates(runtimeContext, extractionMessages);
        verify(memoryExtractionService).extractProfileCandidates(runtimeContext, extractionMessages);
        verify(memoryWriteService).persistCandidates(executionCandidates);
        verify(memoryWriteService).persistCandidates(experienceCandidates);
        verify(memoryWriteService).persistCandidates(profileCandidates);
    }

    @Test
    void preFlushBeforeCompactionShouldPrependRuntimeFixedMessages() {
        SuperAgentContext context = new SuperAgentContext();
        context.setFixedMessages(new ArrayList<>(List.of(
                new SystemMessage("stage-system-prompt"),
                new UserMessage("Current user id: user-1"))));
        List<Message> oldDialogueMessages = List.of(
                new UserMessage("older-user"),
                new AssistantMessage("older-assistant"));
        List<Message> extractionMessages = List.of(
                new SystemMessage("stage-system-prompt"),
                new UserMessage("Current user id: user-1"),
                new UserMessage("older-user"),
                new AssistantMessage("older-assistant"));

        List<MemoryItem> candidates = List.of(new MemoryItem());
        when(memoryExtractionService.extractCompactionCandidates(context, extractionMessages)).thenReturn(candidates);

        memoryLifecycleManager.preFlushBeforeCompaction(context, oldDialogueMessages);

        verify(memoryExtractionService).extractCompactionCandidates(context, extractionMessages);
        verify(memoryWriteService).persistCandidates(candidates);
    }
}
