package org.gemo.apex.memory.session;

import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.domain.Plan;
import org.gemo.apex.domain.Stage;
import org.gemo.apex.domain.interaction.InteractionType;
import org.gemo.apex.domain.interaction.PendingHumanInteraction;
import org.gemo.apex.domain.interaction.PendingToolExecution;
import org.gemo.apex.memory.model.MemoryRecallPackage;
import org.gemo.apex.memory.persistence.convert.SessionContextEntityConverter;
import org.gemo.apex.memory.persistence.entity.AgentSessionDialogueMessageEntity;
import org.gemo.apex.memory.persistence.entity.AgentSessionDialogueSummaryEntity;
import org.gemo.apex.memory.persistence.entity.AgentSessionEntity;
import org.gemo.apex.skills.definition.skill.Skill;
import org.gemo.apex.skills.Skills;
import org.gemo.apex.skills.learning.model.SkillSessionMessage;
import org.gemo.apex.util.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class InMemorySessionContextStoreTest {

    private final InMemorySessionContextStore store = new InMemorySessionContextStore();

    @Test
    void saveAndLoadShouldMatchJdbcSemanticsAndIsolateReferences() {
        SuperAgentContext context = buildContext();
        context.setMemoryRecallPackage(recallPackage());
        store.save(context);
        store.compactDialogue("session-1", new SystemMessage("summary-1"), 2L, 2);
        store.appendDialogueMessages("session-1", 2, 2L, List.of(new UserMessage("user-2"),
                new AssistantMessage("assistant-2")));

        AgentSessionEntity storedSessionEntity = getSessionEntityMap().get("session-1");
        assertNotNull(storedSessionEntity.getRuntimeSnapshot());
        assertNotNull(storedSessionEntity.getFixedMessages());
        Map<String, Object> runtimeSnapshot = JacksonUtils.fromJson(storedSessionEntity.getRuntimeSnapshot(), Map.class);
        assertEquals("stage-runtime-1", runtimeSnapshot.get("currentStageId"));
        assertTrue(runtimeSnapshot.containsKey("plan"));
        assertEquals(Map.of("approved", true), runtimeSnapshot.get("pendingToolResult"));
        assertFalse(runtimeSnapshot.containsKey("userId"));
        assertFalse(runtimeSnapshot.containsKey("agentKey"));
        assertFalse(runtimeSnapshot.containsKey("executionStatus"));
        assertFalse(runtimeSnapshot.containsKey("currentStage"));
        assertFalse(runtimeSnapshot.containsKey("executionMode"));
        assertFalse(runtimeSnapshot.containsKey("lastActiveTime"));
        assertFalse(runtimeSnapshot.containsKey("latestCompressedSortNo"));
        assertFalse(runtimeSnapshot.containsKey("turnStartSortNo"));
        assertFalse(runtimeSnapshot.containsKey("persistedDialogueMessageIndex"));
        assertFalse(runtimeSnapshot.containsKey("nextMessageSortNo"));
        assertEquals(2, ((Number) runtimeSnapshot.get("turnNo")).longValue());
        assertEquals(3, ((Number) runtimeSnapshot.get("iterationNo")).intValue());
        assertFalse(runtimeSnapshot.containsKey("traceNo"));
        assertEquals(List.of("meeting_tool"), runtimeSnapshot.get("enabledToolNames"));

        context.setAgentKey("agent-mutated");
        context.setPendingToolResult(Map.of("approved", false));
        context.setLatestCompressedMessage(new SystemMessage("summary-mutated"));
        context.setDialogueMessages(new ArrayList<>(List.of(new UserMessage("dialogue-mutated"))));
        context.setFixedMessages(new ArrayList<>(List.of(new SystemMessage("fixed-mutated"))));
        context.setAvailableTools(new ArrayList<>());
        context.setSkills(null);
        context.setSseEmitter(null);

        SuperAgentContext loaded = store.load("session-1").orElseThrow();

        assertNotSame(context, loaded);
        assertEquals("session-1", loaded.getSessionId());
        assertEquals("user-1", loaded.getUserId());
        assertEquals("agent-1", loaded.getAgentKey());
        assertEquals(SuperAgentContext.Stage.EXECUTION, loaded.getCurrentStage());
        assertEquals(ModeEnum.REACT, loaded.getExecutionMode());
        assertEquals(ExecutionStatus.IN_PROGRESS, loaded.getExecutionStatus());
        assertEquals("stage-runtime-1", loaded.getCurrentStageId());
        assertEquals(2, loaded.getTurnNo());
        assertEquals(3, loaded.getIterationNo());
        assertEquals(2L, loaded.getLatestCompressedSortNo());
        assertEquals(2L, loaded.getTurnStartSortNo());
        assertEquals(2, loaded.getPersistedDialogueMessageIndex());
        assertEquals(5L, loaded.getNextMessageSortNo());
        assertEquals(LocalDateTime.of(2026, 3, 17, 10, 30, 0), loaded.getLastActiveTime());
        assertNotNull(loaded.getPlan());
        assertEquals("collect requirements", loaded.getPlan().getStages().getFirst().getName());
        assertEquals(Map.of("approved", true), loaded.getPendingToolResult());
        assertEquals("summary-1", getSummaryEntityMap().get("session-1").getContent());
        assertInstanceOf(SystemMessage.class, loaded.getLatestCompressedMessage());
        assertEquals(2, loaded.getDialogueMessages().size());
        assertEquals("user-2", getDialogueMessageMap().get("session-1").get(0).getContent());
        assertInstanceOf(UserMessage.class, loaded.getDialogueMessages().get(0));
        assertEquals("assistant-2", getDialogueMessageMap().get("session-1").get(1).getContent());
        assertInstanceOf(AssistantMessage.class, loaded.getDialogueMessages().get(1));
        assertEquals(1, loaded.getFixedMessages().size());
        assertInstanceOf(SystemMessage.class, loaded.getFixedMessages().getFirst());
        assertEquals("fixed-1", loaded.getFixedMessages().getFirst().getText());
        assertNotNull(loaded.getMemoryRecallPackage());
        assertTrue(loaded.getMemoryRecallPackage().getProfileItems().isEmpty());
        assertTrue(loaded.getMemoryRecallPackage().getExperienceItems().isEmpty());
        assertTrue(loaded.getAvailableTools().isEmpty());
        assertNull(loaded.getSkills());
        assertNull(loaded.getSseEmitter());
        assertEquals(List.of("meeting_tool"), loaded.getEnabledToolNames());

        loaded.setAgentKey("agent-after-load");
        loaded.setPendingToolResult(Map.of("approved", "changed"));
        loaded.setLatestCompressedMessage(new SystemMessage("summary-after-load"));
        loaded.addMessage(new AssistantMessage("assistant-extra"));

        SuperAgentContext reloaded = store.load("session-1").orElseThrow();

        assertEquals("agent-1", reloaded.getAgentKey());
        assertEquals(Map.of("approved", true), reloaded.getPendingToolResult());
        assertInstanceOf(SystemMessage.class, reloaded.getLatestCompressedMessage());
        assertEquals(2, reloaded.getDialogueMessages().size());
        assertInstanceOf(AssistantMessage.class, reloaded.getDialogueMessages().get(1));
    }

    @Test
    void saveAndLoadShouldPersistPendingConfirmationState() {
        SuperAgentContext context = buildContext();
        context.setPendingHumanInteraction(PendingHumanInteraction.builder()
                .interactionType(InteractionType.TOOL_CONFIRMATION.name())
                .toolCallId("call-1")
                .invocationId("invocation-1")
                .confirmationId("confirm-1")
                .build());
        context.setPendingToolExecution(PendingToolExecution.builder()
                .toolCallId("call-1")
                .toolName("meeting_tool")
                .invocationId("invocation-1")
                .resolvedArguments(Map.of("room", "A1001"))
                .editableFieldKeys(List.of("room"))
                .confirmationId("confirm-1")
                .executedPreHookBeans(List.of("mutateRoomHook", "toolConfirmHook"))
                .build());

        store.save(context);

        SuperAgentContext loaded = store.load("session-1").orElseThrow();

        assertEquals("TOOL_CONFIRMATION", loaded.getPendingHumanInteraction().getInteractionType());
        assertEquals("meeting_tool", loaded.getPendingToolExecution().getToolName());
        assertEquals(List.of("room"), loaded.getPendingToolExecution().getEditableFieldKeys());
        assertEquals(List.of("mutateRoomHook", "toolConfirmHook"),
                loaded.getPendingToolExecution().getExecutedPreHookBeans());
    }

    @Test
    void appendCompactAndDeleteShouldPreserveRawMessagesAndFilterLoadView() {
        SuperAgentContext context = buildContext();
        store.save(context);
        store.compactDialogue("session-1", new SystemMessage("summary-1"), 2L, 2);
        store.appendDialogueMessages("session-1", 2, 2L, List.of(new UserMessage("user-2"),
                new AssistantMessage("assistant-2")));

        LocalDateTime originalCreateTime = getSessionEntityMap().get("session-1").getCreateTime();
        assertNotNull(getSummaryEntityMap().get("session-1"));
        assertEquals(2, getDialogueMessageMap().get("session-1").size());

        store.appendDialogueMessages("session-1", 3, 4L, List.of(new UserMessage("user-replacement")));
        store.compactDialogue("session-1", new SystemMessage("summary-2"), 4L, 3);

        context.setLatestCompressedMessage(new SystemMessage("summary-2"));
        context.setLatestCompressedSortNo(4L);
        context.setTurnStartSortNo(4L);
        context.setDialogueMessages(new ArrayList<>(List.of(new UserMessage("user-replacement"))));
        context.setPersistedDialogueMessageIndex(1);
        context.setNextMessageSortNo(6L);
        context.setTurnNo(3);
        store.save(context);

        AgentSessionEntity updatedSessionEntity = getSessionEntityMap().get("session-1");
        assertEquals(originalCreateTime, updatedSessionEntity.getCreateTime());
        assertEquals("summary-2", getSummaryEntityMap().get("session-1").getContent());
        assertEquals(3, getDialogueMessageMap().get("session-1").size());
        assertTrue(Boolean.TRUE.equals(getDialogueMessageMap().get("session-1").get(0).getCompacted()));
        assertTrue(Boolean.TRUE.equals(getDialogueMessageMap().get("session-1").get(1).getCompacted()));
        assertFalse(Boolean.TRUE.equals(getDialogueMessageMap().get("session-1").get(2).getCompacted()));

        SuperAgentContext loaded = store.load("session-1").orElseThrow();
        assertInstanceOf(SystemMessage.class, loaded.getLatestCompressedMessage());
        assertEquals("summary-2", loaded.getLatestCompressedMessage().getText());
        assertEquals(4L, loaded.getLatestCompressedSortNo());
        assertEquals(4L, loaded.getTurnStartSortNo());
        assertEquals(1, loaded.getDialogueMessages().size());
        assertInstanceOf(UserMessage.class, loaded.getDialogueMessages().getFirst());
        assertEquals(6L, loaded.getNextMessageSortNo());

        List<Message> rawMessages = store.loadAllRawDialogueMessages("session-1");
        assertEquals(3, rawMessages.size());
        assertInstanceOf(UserMessage.class, rawMessages.get(0));
        assertInstanceOf(AssistantMessage.class, rawMessages.get(1));
        assertInstanceOf(UserMessage.class, rawMessages.get(2));

        store.delete("session-1");

        assertTrue(store.load("session-1").isEmpty());
        assertFalse(getSessionEntityMap().containsKey("session-1"));
        assertFalse(getDialogueMessageMap().containsKey("session-1"));
        assertFalse(getSummaryEntityMap().containsKey("session-1"));
    }

    @Test
    void loadShouldFallbackToSummaryTurnWhenDialogueMessagesAreEmpty() {
        SuperAgentContext context = buildContext();
        context.setDialogueMessages(new ArrayList<>());
        context.setPersistedDialogueMessageIndex(0);
        context.setNextMessageSortNo(3L);
        context.setTurnNo(2);
        store.save(context);

        AgentSessionDialogueSummaryEntity summaryEntity = SessionContextEntityConverter.toSummaryEntity("session-1",
                new SystemMessage("summary-only"), 8L, 4, LocalDateTime.of(2026, 3, 17, 10, 0, 0));
        getSummaryEntityMap().put("session-1", summaryEntity);
        getDialogueMessageMap().put("session-1", new ArrayList<>());

        SuperAgentContext loaded = store.load("session-1").orElseThrow();

        assertEquals(4, loaded.getTurnNo());
        assertEquals(8L, loaded.getLatestCompressedSortNo());
        assertEquals(8L, loaded.getTurnStartSortNo());
        assertEquals(0, loaded.getPersistedDialogueMessageIndex());
        assertEquals(9L, loaded.getNextMessageSortNo());
    }

    @Test
    void loadSkillSessionMessagesShouldReturnSortOrderedProjection() {
        store.appendDialogueMessages("session-1", 2, 0L, List.of(
                new UserMessage("user-1"),
                AssistantMessage.builder()
                        .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "activate_skill",
                                "{\"command\":\"writing-plans\"}")))
                        .build()));

        List<SkillSessionMessage> messages = store.loadSkillSessionMessages("session-1");

        assertEquals(2, messages.size());
        assertEquals(1L, messages.get(0).getSortNo());
        assertEquals("user", messages.get(0).getRole());
        assertEquals(2L, messages.get(1).getSortNo());
        assertEquals("activate_skill", messages.get(1).getToolName());
        assertNotNull(messages.get(1).getMessagePayload());
    }

    private SuperAgentContext buildContext() {
        SuperAgentContext context = new SuperAgentContext();
        context.setSessionId("session-1");
        context.setUserId("user-1");
        context.setAgentKey("agent-1");
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        context.setExecutionMode(ModeEnum.REACT);
        context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);
        context.setCurrentStageId("stage-runtime-1");
        context.setLatestCompressedMessage(new SystemMessage("summary-1"));
        context.setLatestCompressedSortNo(2L);
        context.setTurnStartSortNo(2L);
        context.setDialogueMessages(new ArrayList<>(List.of(
                new UserMessage("user-2"),
                new AssistantMessage("assistant-2"))));
        context.setPersistedDialogueMessageIndex(2);
        context.setNextMessageSortNo(5L);
        context.setTurnNo(2);
        context.setIterationNo(3);
        context.setLastActiveTime(LocalDateTime.of(2026, 3, 17, 10, 30, 0));
        context.setPendingToolResult(Map.of("approved", true));
        context.setPlan(buildPlan());
        context.setFixedMessages(new ArrayList<>(List.of(new SystemMessage("fixed-1"))));
        context.setAvailableTools(new ArrayList<>(List.of(mock(ToolCallback.class))));
        context.setEnabledToolNames(new ArrayList<>(List.of("meeting_tool")));
        context.setSkills(Skills.from(Skill.builder()
                .name("demo-skill")
                .description("demo description")
                .content("demo instructions")
                .build()));
        context.setSseEmitter(new SseEmitter());
        return context;
    }

    private Plan buildPlan() {
        Stage stage = new Stage();
        stage.setId("plan-stage-1");
        stage.setName("collect requirements");

        Plan plan = new Plan();
        plan.setStages(new ArrayList<>(List.of(stage)));
        return plan;
    }

    private MemoryRecallPackage recallPackage() {
        MemoryRecallPackage recallPackage = new MemoryRecallPackage();
        recallPackage.setProfileItems(List.of(new org.gemo.apex.memory.model.MemoryItem()));
        recallPackage.setExperienceItems(List.of(new org.gemo.apex.memory.model.MemoryItem()));
        return recallPackage;
    }

    @SuppressWarnings("unchecked")
    private Map<String, AgentSessionEntity> getSessionEntityMap() {
        return (Map<String, AgentSessionEntity>) ReflectionTestUtils.getField(store, "sessionEntityMap");
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<AgentSessionDialogueMessageEntity>> getDialogueMessageMap() {
        return (Map<String, List<AgentSessionDialogueMessageEntity>>) ReflectionTestUtils.getField(store,
                "dialogueMessageMap");
    }

    @SuppressWarnings("unchecked")
    private Map<String, AgentSessionDialogueSummaryEntity> getSummaryEntityMap() {
        return (Map<String, AgentSessionDialogueSummaryEntity>) ReflectionTestUtils.getField(store, "summaryEntityMap");
    }
}
