package org.gemo.apex.skills.learning;

import org.gemo.apex.skills.learning.model.SkillConversationSlice;
import org.gemo.apex.skills.learning.model.SkillSessionMessage;
import org.gemo.apex.skills.learning.model.SkillUsageRecord;
import org.gemo.apex.skills.learning.model.SkillUsageValidationResult;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.util.JacksonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class SkillUsageMessageCollectorTest {

    @Mock
    private SessionContextStore sessionContextStore;

    private SkillUsageMessageCollector collector;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SkillExperienceLearningProperties properties = new SkillExperienceLearningProperties();
        collector = new SkillUsageMessageCollector(properties, sessionContextStore);
    }

    @Test
    void collectShouldUseFullSessionForShortSessions() {
        when(sessionContextStore.loadSkillSessionMessages("session-1")).thenReturn(List.of(
                message(9L, "user", null, null, "Need a plan"),
                activationMessage(10L, "writing-plans"),
                message(11L, "tool", "activate_skill", null,
                        "<activated_skill name=\"writing-plans\">...</activated_skill>"),
                message(12L, "assistant", null, null, "Plan incoming")));

        List<SkillConversationSlice> slices = collector.collectValidSlices(List.of(
                SkillUsageRecord.builder()
                        .id("u1")
                        .agentKey("default_agent")
                        .skillName("writing-plans")
                        .sessionId("session-1")
                        .activationMessageSortNo(10L)
                        .build()));

        assertEquals(1, slices.size());
        assertEquals(List.of(9L, 10L, 11L, 12L),
                slices.getFirst().getMessages().stream().map(SkillSessionMessage::getSortNo).toList());
    }

    @Test
    void validateShouldRejectMismatchedSkillName() {
        when(sessionContextStore.loadSkillSessionMessages("session-2")).thenReturn(List.of(
                message(20L, "user", null, null, "hi"),
                activationMessage(21L, "wrong-skill"),
                message(22L, "tool", "activate_skill", null, "bad")));

        SkillUsageValidationResult result = collector.validate(SkillUsageRecord.builder()
                .id("u2")
                .agentKey("default_agent")
                .skillName("writing-plans")
                .sessionId("session-2")
                .activationMessageSortNo(21L)
                .build());

        assertFalse(result.isValid());
        assertTrue(result.reason().contains("skill_name mismatch"));
    }

    private SkillSessionMessage activationMessage(long sortNo, String skillName) {
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + sortNo, "function", "activate_skill",
                        "{\"command\":\"" + skillName + "\"}")))
                .build();
        return new SkillSessionMessage(sortNo, "assistant", "activate_skill", JacksonUtils.toJson(assistantMessage), "");
    }

    private SkillSessionMessage message(long sortNo, String role, String toolName, String payload, String content) {
        return new SkillSessionMessage(sortNo, role, toolName, payload, content);
    }
}
