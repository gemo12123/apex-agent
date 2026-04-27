package org.gemo.apex.memory.persistence.convert;

import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.persistence.entity.AgentSessionEntity;
import org.gemo.apex.util.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionContextEntityConverterTest {

    @Test
    void fromEntitiesShouldRejectLegacyCurrentStageValues() {
        AgentSessionEntity sessionEntity = new AgentSessionEntity();
        sessionEntity.setSessionId("session-1");
        sessionEntity.setAgentKey("agent-1");
        sessionEntity.setUserId("user-1");
        sessionEntity.setCurrentStage("THINKING");
        sessionEntity.setExecutionMode(ModeEnum.REACT.name());
        sessionEntity.setExecutionStatus(ExecutionStatus.IN_PROGRESS.name());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> SessionContextEntityConverter.fromEntities(sessionEntity, null, List.of(), 1));

        assertTrue(exception.getMessage().contains("EXECUTION"));
    }

    @Test
    void fromEntitiesShouldRestorePersistedFixedMessages() {
        AgentSessionEntity sessionEntity = new AgentSessionEntity();
        sessionEntity.setSessionId("session-1");
        sessionEntity.setAgentKey("agent-1");
        sessionEntity.setUserId("user-1");
        sessionEntity.setCurrentStage("EXECUTION");
        sessionEntity.setExecutionMode(ModeEnum.REACT.name());
        sessionEntity.setExecutionStatus(ExecutionStatus.IN_PROGRESS.name());
        sessionEntity.setFixedMessages(JacksonUtils.toJson(List.of(
                new SystemMessage("stage-system-prompt"),
                new UserMessage("Current user id: user-1"))));

        SuperAgentContext context = SessionContextEntityConverter.fromEntities(sessionEntity, null, List.of(), 0);

        assertEquals(2, context.getFixedMessages().size());
        assertInstanceOf(SystemMessage.class, context.getFixedMessages().get(0));
        assertEquals("stage-system-prompt", context.getFixedMessages().get(0).getText());
        assertInstanceOf(UserMessage.class, context.getFixedMessages().get(1));
        assertEquals("Current user id: user-1", context.getFixedMessages().get(1).getText());
    }
}
