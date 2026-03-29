package org.gemo.apex.core;

import org.gemo.apex.component.tool.BuiltInToolProvider;
import org.gemo.apex.component.tool.GlobalToolRegistry;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.domain.Plan;
import org.gemo.apex.memory.context.UserContextHolder;
import org.gemo.apex.memory.model.MemoryRecallPackage;
import org.gemo.apex.memory.recall.MemoryRecallService;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.service.AgentWorkspaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuperAgentFactoryTest {

    @Mock
    private GlobalToolRegistry globalToolRegistry;

    @Mock
    private BuiltInToolProvider builtInToolProvider;

    @Mock
    private AgentWorkspaceService agentWorkspaceService;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private SessionContextStore sessionContextStore;

    @Mock
    private MemoryRecallService memoryRecallService;

    @InjectMocks
    private SuperAgentFactory superAgentFactory;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        UserContextHolder.setUserId("user-1");
        when(builtInToolProvider.getBuiltInTools()).thenReturn(List.of());
        when(globalToolRegistry.getMcpToolCallbacks(anyString())).thenReturn(List.of());
        when(globalToolRegistry.getSubAgentToolCallbacks(anyString())).thenReturn(List.of());
        when(globalToolRegistry.getSkillsTool(anyString())).thenReturn(null);
        when(agentWorkspaceService.getDefaultExecutionMode(anyString())).thenReturn(ModeEnum.REACT);
        when(memoryRecallService.recall(any(SuperAgentContext.class))).thenReturn(new MemoryRecallPackage());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void createContextShouldCreateNewSessionWhenSessionDoesNotExist() {
        when(sessionContextStore.load("session-1")).thenReturn(Optional.empty());

        SuperAgentContext context = superAgentFactory.createContext("session-1", "agent-1", "hello");

        assertEquals("session-1", context.getSessionId());
        assertEquals("agent-1", context.getAgentKey());
        assertEquals("user-1", context.getUserId());
        assertEquals(1, context.getTurnNo());
        assertEquals(SuperAgentContext.Stage.EXECUTION, context.getCurrentStage());
        assertEquals(ModeEnum.REACT, context.getExecutionMode());
        assertEquals(1, context.getPersistedDialogueMessageIndex());
        assertEquals(2L, context.getNextMessageSortNo());
        assertEquals(1, context.getDialogueMessages().size());
        assertInstanceOf(UserMessage.class, context.getDialogueMessages().getFirst());
        assertEquals("hello", context.getDialogueMessages().getFirst().getText());

        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionContextStore).appendDialogueMessages(eq("session-1"), eq(1), eq(0L), messagesCaptor.capture());
        assertEquals(1, messagesCaptor.getValue().size());
        assertEquals("hello", messagesCaptor.getValue().getFirst().getText());
        verify(sessionContextStore).save(context);
    }

    @Test
    void createContextShouldFailFastWhenDefaultExecutionModeMissing() {
        when(sessionContextStore.load("session-2")).thenReturn(Optional.empty());
        when(agentWorkspaceService.getDefaultExecutionMode("agent-1")).thenReturn(null);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> superAgentFactory.createContext("session-2", "agent-1", "hello"));

        assertTrue(exception.getMessage().contains("default execution mode"));
        verify(sessionContextStore, never()).save(any(SuperAgentContext.class));
    }

    @Test
    void createContextShouldStartNextTurnForExistingSession() {
        SuperAgentContext existingContext = new SuperAgentContext();
        existingContext.setSessionId("session-1");
        existingContext.setUserId("user-1");
        existingContext.setAgentKey("agent-1");
        existingContext.setExecutionStatus(ExecutionStatus.COMPLETED);
        existingContext.setTurnNo(2);
        existingContext.setLatestCompressedSortNo(4L);
        existingContext.setDialogueMessages(new ArrayList<>(List.of(new UserMessage("history"))));
        existingContext.setPersistedDialogueMessageIndex(1);
        existingContext.setPlan(new Plan());
        existingContext.setCurrentStageId("stage-1");
        existingContext.setPendingToolResult(java.util.Map.of("approved", true));
        MemoryRecallPackage recallPackage = new MemoryRecallPackage();
        when(memoryRecallService.recall(any(SuperAgentContext.class))).thenReturn(recallPackage);
        when(sessionContextStore.load("session-1")).thenReturn(Optional.of(existingContext));

        SuperAgentContext context = superAgentFactory.createContext("session-1", "agent-1", "follow up");

        assertSame(existingContext, context);
        assertEquals(3, context.getTurnNo());
        assertEquals(SuperAgentContext.Stage.EXECUTION, context.getCurrentStage());
        assertEquals(ModeEnum.REACT, context.getExecutionMode());
        assertEquals(4L, context.getTurnStartSortNo());
        assertEquals(2, context.getPersistedDialogueMessageIndex());
        assertEquals(7L, context.getNextMessageSortNo());
        assertNull(context.getPlan());
        assertNull(context.getCurrentStageId());
        assertNull(context.getPendingToolResult());
        assertSame(recallPackage, context.getMemoryRecallPackage());
        assertEquals(2, context.getDialogueMessages().size());
        assertEquals("follow up", context.getDialogueMessages().getLast().getText());

        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionContextStore).appendDialogueMessages(eq("session-1"), eq(3), eq(5L), messagesCaptor.capture());
        assertEquals(1, messagesCaptor.getValue().size());
        assertEquals("follow up", messagesCaptor.getValue().getFirst().getText());
        verify(sessionContextStore).save(context);
    }

    @Test
    void createContextShouldRejectSuspendedSessionForNewRequest() {
        SuperAgentContext existingContext = new SuperAgentContext();
        existingContext.setSessionId("session-1");
        existingContext.setUserId("user-1");
        existingContext.setAgentKey("agent-1");
        existingContext.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
        when(sessionContextStore.load("session-1")).thenReturn(Optional.of(existingContext));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> superAgentFactory.createContext("session-1", "agent-1", "follow up"));

        assertTrue(exception.getMessage().contains("HUMAN_RESPONSE"));
        verify(sessionContextStore, never()).appendDialogueMessages(anyString(), any(), anyLong(), any());
        verify(sessionContextStore, never()).save(any(SuperAgentContext.class));
    }

    @Test
    void createContextShouldRejectSessionOfAnotherUser() {
        SuperAgentContext existingContext = new SuperAgentContext();
        existingContext.setSessionId("session-1");
        existingContext.setUserId("user-2");
        existingContext.setAgentKey("agent-1");
        existingContext.setExecutionStatus(ExecutionStatus.COMPLETED);
        when(sessionContextStore.load("session-1")).thenReturn(Optional.of(existingContext));

        assertThrows(IllegalStateException.class,
                () -> superAgentFactory.createContext("session-1", "agent-1", "follow up"));

        verify(sessionContextStore, never()).appendDialogueMessages(anyString(), any(), anyLong(), any());
        verify(sessionContextStore, never()).save(any(SuperAgentContext.class));
    }

    @Test
    void createContextShouldRejectSessionOfAnotherAgent() {
        SuperAgentContext existingContext = new SuperAgentContext();
        existingContext.setSessionId("session-1");
        existingContext.setUserId("user-1");
        existingContext.setAgentKey("agent-2");
        existingContext.setExecutionStatus(ExecutionStatus.COMPLETED);
        when(sessionContextStore.load("session-1")).thenReturn(Optional.of(existingContext));

        assertThrows(IllegalStateException.class,
                () -> superAgentFactory.createContext("session-1", "agent-1", "follow up"));

        verify(sessionContextStore, never()).appendDialogueMessages(anyString(), any(), anyLong(), any());
        verify(sessionContextStore, never()).save(any(SuperAgentContext.class));
    }
}
