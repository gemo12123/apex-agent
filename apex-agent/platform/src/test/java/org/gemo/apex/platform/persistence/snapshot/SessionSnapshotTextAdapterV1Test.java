package org.gemo.apex.platform.persistence.snapshot;

import org.gemo.apex.platform.PlatformFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionSnapshotTextAdapterV1Test {
    @Test
    void v1快照及挂起交互应完整往返() {
        var adapter = new SessionSnapshotTextAdapterV1();
        var snapshot = PlatformFixtures.suspendedSnapshot();
        assertEquals(snapshot, adapter.decode(adapter.encode(snapshot)));
    }

    @Test
    void 未知版本应显式拒绝() {
        var adapter = new SessionSnapshotTextAdapterV1();
        var entity = adapter.encode(PlatformFixtures.suspendedSnapshot());
        var invalid = new org.gemo.apex.platform.persistence.session.AgentSessionEntity(entity.sessionId(),
                entity.userId(), entity.agentKey(), entity.status(), entity.currentTurnNo(),
                entity.agentDefinitionSnapshot(), entity.enabledToolNames(), entity.activatedSkillNames(),
                entity.runtimeSnapshot().replace("1.0.0", "2.0.0"), entity.suspendedToolCall(),
                entity.lastActiveTime());
        assertThrows(org.gemo.apex.common.exception.UnsupportedSnapshotVersionException.class,
                () -> adapter.decode(invalid));
    }
}
