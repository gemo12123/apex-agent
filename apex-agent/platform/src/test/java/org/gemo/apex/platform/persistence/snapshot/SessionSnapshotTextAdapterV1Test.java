package org.gemo.apex.platform.persistence.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.gemo.apex.common.exception.InvalidSnapshotException;
import org.gemo.apex.common.exception.UnsupportedSnapshotVersionException;
import org.gemo.apex.platform.PlatformFixtures;
import org.gemo.apex.platform.persistence.session.AgentSessionEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SessionSnapshotTextAdapterV1Test {
    /** v1快照及挂起交互应完整往返 */
    @Test
    void v1SnapshotsAndSuspendedInteractionsRoundTripCompletely() {
        var adapter = new SessionSnapshotTextAdapterV1();
        var snapshot = PlatformFixtures.suspendedSnapshot();
        assertEquals(snapshot, adapter.decode(adapter.encode(snapshot)));
    }

    /** 未知版本应显式拒绝 */
    @Test
    void rejectsUnknownVersionExplicitly() {
        var adapter = new SessionSnapshotTextAdapterV1();
        var entity = adapter.encode(PlatformFixtures.suspendedSnapshot());
        var invalid =
                new AgentSessionEntity(
                        entity.sessionId(),
                        entity.userId(),
                        entity.agentKey(),
                        entity.status(),
                        entity.currentTurnNo(),
                        entity.agentDefinitionSnapshot(),
                        entity.enabledToolNames(),
                        entity.activatedSkillNames(),
                        entity.runtimeSnapshot().replace("1.0.0", "2.0.0"),
                        entity.suspendedToolCall(),
                        entity.lastActiveTime());
        assertThrows(UnsupportedSnapshotVersionException.class, () -> adapter.decode(invalid));
    }

    @Test
    void rejectsLegacySingleSuspendedToolCallExplicitly() {
        var adapter = new SessionSnapshotTextAdapterV1();
        var entity = adapter.encode(PlatformFixtures.suspendedSnapshot());
        var legacy =
                new AgentSessionEntity(
                        entity.sessionId(),
                        entity.userId(),
                        entity.agentKey(),
                        entity.status(),
                        entity.currentTurnNo(),
                        entity.agentDefinitionSnapshot(),
                        entity.enabledToolNames(),
                        entity.activatedSkillNames(),
                        entity.runtimeSnapshot(),
                        "{\"toolCallId\":\"call-1\",\"toolName\":\"ask\"}",
                        entity.lastActiveTime());

        var exception = assertThrows(InvalidSnapshotException.class, () -> adapter.decode(legacy));
        Assertions.assertTrue(exception.getMessage().contains("旧版单条挂起快照"));
    }
}
