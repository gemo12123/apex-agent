package org.gemo.apex.memory.persistence.convert;

import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.memory.persistence.entity.AgentSessionEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
