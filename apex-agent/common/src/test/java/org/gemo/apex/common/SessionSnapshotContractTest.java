package org.gemo.apex.common;

import org.gemo.apex.common.exception.InvalidSnapshotException;
import org.gemo.apex.common.exception.UnsupportedSnapshotVersionException;
import org.gemo.apex.common.exception.SnapshotDecodingException;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.snapshot.*;
import org.gemo.apex.common.tool.CancellationToken;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SessionSnapshotContractTest {
    @Test
    void v1快照应完整往返并按ToolCallId定位挂起调用() {
        SessionSnapshot snapshot = CommonFixtures.suspendedSnapshot();
        SessionSnapshotJsonAdapter adapter = new SessionSnapshotJsonAdapter();

        SessionSnapshot copy = adapter.read(adapter.write(snapshot));

        assertEquals(snapshot, copy);
        assertEquals("call-1", copy.suspendedToolCall().toolCallId());
        assertEquals(List.of("audit", "confirm"), copy.suspendedToolCall().executedPreToolHookIds());
    }

    @Test
    void 未知版本应显式拒绝且不尝试升级() {
        String json = new SessionSnapshotJsonAdapter().write(CommonFixtures.suspendedSnapshot())
                .replace("\"1.0.0\"", "\"2.0.0\"");
        assertThrows(UnsupportedSnapshotVersionException.class,
                () -> new SessionSnapshotJsonAdapter().read(json));
    }

    @Test
    void 损坏Json应包装为不泄露正文的快照解码异常() {
        SnapshotDecodingException exception = assertThrows(SnapshotDecodingException.class,
                () -> new SessionSnapshotJsonAdapter().read("{not-json"));
        assertFalse(exception.getMessage().contains("not-json"));
    }

    @Test
    void 历史工具不能回填enabledTools且preHookId不能重复() {
        SessionSnapshot source = CommonFixtures.suspendedSnapshot();
        HistoricalToolBinding historical = new HistoricalToolBinding("search",
                org.gemo.apex.common.tool.ToolOrigin.MCP, "github", "DOWN", CommonFixtures.NOW);
        assertThrows(InvalidSnapshotException.class, () -> new SessionSnapshot(
                source.schemaVersion(), source.sessionId(), source.userId(), source.agentKey(), source.status(),
                source.currentTurnNo(), source.enabledTools(), source.activatedSkills(), List.of(historical),
                source.activeDefinition(), source.activeTurn(), source.suspendedToolCall(),
                source.nextMessageSortNo(), source.lastActiveTime()));
        SuspendedToolCall suspended = source.suspendedToolCall();
        assertThrows(IllegalArgumentException.class, () -> new SuspendedToolCall(
                suspended.sessionId(), suspended.turnNo(), suspended.iterationNo(), suspended.toolCallId(),
                suspended.invocationId(), suspended.toolName(), suspended.resolvedArguments(),
                suspended.intervention(), List.of("same", "same"), suspended.suspensionPoint()));
    }

    @Test
    void 快照类型图不得包含CancellationToken() {
        Set<Class<?>> seen = new HashSet<>();
        ArrayDeque<Class<?>> queue = new ArrayDeque<>();
        queue.add(SessionSnapshot.class);
        while (!queue.isEmpty()) {
            Class<?> type = queue.removeFirst();
            if (!seen.add(type) || !type.isRecord()) continue;
            for (RecordComponent component : type.getRecordComponents()) {
                assertNotEquals(CancellationToken.class, component.getType());
                if (component.getType().getPackageName().startsWith("org.gemo.apex.common")) {
                    queue.add(component.getType());
                }
            }
        }
    }
}
