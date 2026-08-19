package org.gemo.apex.platform.persistence.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.gemo.apex.common.exception.InvalidSnapshotException;
import org.gemo.apex.common.exception.UnsupportedSnapshotVersionException;
import org.gemo.apex.common.execution.IterationStatus;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.execution.TurnStatus;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.shared.SharedDataCleanupPolicy;
import org.gemo.apex.common.shared.SharedDataEntry;
import org.gemo.apex.common.snapshot.ExecutionErrorSnapshot;
import org.gemo.apex.common.snapshot.ExecutionErrorType;
import org.gemo.apex.common.snapshot.IterationSnapshot;
import org.gemo.apex.common.snapshot.SessionSnapshot;
import org.gemo.apex.common.snapshot.TurnSnapshot;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.platform.PlatformFixtures;
import org.gemo.apex.platform.persistence.session.AgentSessionEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SessionSnapshotTextAdapterV1Test {
    @Test
    void persistsSharedDataAndDefaultsMissingLegacyFieldToEmpty() {
        var adapter = new SessionSnapshotTextAdapterV1();
        var source = PlatformFixtures.suspendedSnapshot();
        var snapshot =
                new SessionSnapshot(
                        source.schemaVersion(),
                        source.sessionId(),
                        source.userId(),
                        source.agentKey(),
                        source.status(),
                        source.currentTurnNo(),
                        source.enabledTools(),
                        source.activatedSkills(),
                        source.historicalToolBindings(),
                        source.activeDefinition(),
                        source.activeTurn(),
                        source.suspendedToolBatch(),
                        Map.of(
                                "state",
                                new SharedDataEntry(
                                        SharedDataCleanupPolicy.NEVER, Map.of("count", 1))),
                        List.of(
                                new ExecutionErrorSnapshot(
                                        1,
                                        1,
                                        ExecutionErrorType.HOOK,
                                        HookPoint.PRE_MODEL_CALL,
                                        "guard",
                                        "第 1 个 Turn 第 1 轮的 PRE_MODEL_CALL Hook guard 执行失败",
                                        PlatformFixtures.NOW)),
                        source.nextMessageSortNo(),
                        source.lastActiveTime());

        var entity = adapter.encode(snapshot);
        assertEquals(snapshot.sharedData(), adapter.decode(entity).sharedData());
        assertEquals(snapshot.executionErrors(), adapter.decode(entity).executionErrors());
        Assertions.assertTrue(entity.runtimeSnapshot().contains("sharedData"));
        Assertions.assertTrue(entity.runtimeSnapshot().contains("executionErrors"));

        var legacyTree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        JsonUtils.parseTree(entity.runtimeSnapshot());
        legacyTree.remove("sharedData");
        legacyTree.remove("executionErrors");
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
                        JsonUtils.toJson(legacyTree),
                        entity.suspendedToolCall(),
                        entity.lastActiveTime());
        assertEquals(Map.of(), adapter.decode(legacy).sharedData());
        assertEquals(List.of(), adapter.decode(legacy).executionErrors());
    }

    /** 挂起快照只保留恢复投影，不复制完整模型请求和响应。 */
    @Test
    void suspendedSnapshotPersistsOnlyRecoveryProjection() {
        var adapter = new SessionSnapshotTextAdapterV1();
        var snapshot = PlatformFixtures.suspendedSnapshot();
        var entity = adapter.encode(snapshot);

        assertFalse(entity.runtimeSnapshot().contains("modelRequest"));
        assertFalse(entity.runtimeSnapshot().contains("modelResponse"));
        assertFalse(entity.runtimeSnapshot().contains("hello"));
        assertFalse(entity.runtimeSnapshot().contains("system"));
        Assertions.assertTrue(entity.runtimeSnapshot().contains("suspendedToolBatch"));
        assertNull(entity.suspendedToolCall());

        var decoded = adapter.decode(entity);
        var iteration = decoded.activeTurn().currentIteration();
        assertNull(iteration.modelRequest());
        assertNull(iteration.modelResponse().text());
        assertEquals(Map.of(), iteration.modelResponse().metadata());
        assertEquals(
                snapshot.suspendedToolBatch().toolCalls().getFirst().resolvedArguments(),
                iteration.modelResponse().toolCalls().getFirst().arguments());
        assertEquals(
                snapshot.suspendedToolBatch().toolCalls().getFirst().toolCallMetadata(),
                iteration.modelResponse().toolCalls().getFirst().metadata());
    }

    /** 旧版批次列仍可读取，但新写入统一进入runtime_snapshot。 */
    @Test
    void readsLegacySuspendedBatchColumn() {
        var adapter = new SessionSnapshotTextAdapterV1();
        var snapshot = PlatformFixtures.suspendedSnapshot();
        var current = adapter.encode(snapshot);
        var state =
                JsonUtils.fromJson(
                        current.runtimeSnapshot(), SessionSnapshotTextAdapterV1.RuntimeState.class);
        var legacyRuntime =
                JsonUtils.toJson(
                        new SessionSnapshotTextAdapterV1.RuntimeState(
                                state.schemaVersion(),
                                state.historicalToolBindings(),
                                state.activeTurn(),
                                null,
                                state.nextMessageSortNo()));
        var legacy =
                new AgentSessionEntity(
                        current.sessionId(),
                        current.userId(),
                        current.agentKey(),
                        current.status(),
                        current.currentTurnNo(),
                        current.agentDefinitionSnapshot(),
                        current.enabledToolNames(),
                        current.activatedSkillNames(),
                        legacyRuntime,
                        JsonUtils.toJson(snapshot.suspendedToolBatch()),
                        current.lastActiveTime());

        assertEquals(snapshot.suspendedToolBatch(), adapter.decode(legacy).suspendedToolBatch());
    }

    /** 终态只保留Iteration元数据。 */
    @Test
    void terminalSnapshotDropsIterationPayload() {
        var adapter = new SessionSnapshotTextAdapterV1();
        var source = PlatformFixtures.suspendedSnapshot();
        var old = source.activeTurn().currentIteration();
        var iteration =
                new IterationSnapshot(
                        old.iterationNo(),
                        IterationStatus.COMPLETED,
                        old.modelRequest(),
                        old.modelResponse(),
                        List.of(new ToolResult("call-1", "search", "large-result", Map.of())),
                        old.startedTime(),
                        PlatformFixtures.NOW);
        var turn =
                new TurnSnapshot(
                        source.currentTurnNo(),
                        TurnStatus.COMPLETED,
                        iteration,
                        source.activeTurn().startedTime(),
                        PlatformFixtures.NOW);
        var terminal =
                new SessionSnapshot(
                        source.schemaVersion(),
                        source.sessionId(),
                        source.userId(),
                        source.agentKey(),
                        SessionStatus.COMPLETED,
                        source.currentTurnNo(),
                        source.enabledTools(),
                        source.activatedSkills(),
                        source.historicalToolBindings(),
                        source.activeDefinition(),
                        turn,
                        null,
                        source.nextMessageSortNo(),
                        source.lastActiveTime());

        var decoded = adapter.decode(adapter.encode(terminal));
        var persisted = decoded.activeTurn().currentIteration();
        assertEquals(iteration.iterationNo(), persisted.iterationNo());
        assertEquals(iteration.status(), persisted.status());
        assertNull(persisted.modelRequest());
        assertNull(persisted.modelResponse());
        assertEquals(List.of(), persisted.completedToolResults());
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
        var state =
                JsonUtils.fromJson(
                        entity.runtimeSnapshot(), SessionSnapshotTextAdapterV1.RuntimeState.class);
        var legacyRuntime =
                JsonUtils.toJson(
                        new SessionSnapshotTextAdapterV1.RuntimeState(
                                state.schemaVersion(),
                                state.historicalToolBindings(),
                                state.activeTurn(),
                                null,
                                state.nextMessageSortNo()));
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
                        legacyRuntime,
                        "{\"toolCallId\":\"call-1\",\"toolName\":\"ask\"}",
                        entity.lastActiveTime());

        var exception = assertThrows(InvalidSnapshotException.class, () -> adapter.decode(legacy));
        Assertions.assertTrue(exception.getMessage().contains("旧版单条挂起快照"));
    }
}
