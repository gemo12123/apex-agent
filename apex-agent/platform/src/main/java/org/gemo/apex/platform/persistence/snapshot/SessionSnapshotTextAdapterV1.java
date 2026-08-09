package org.gemo.apex.platform.persistence.snapshot;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gemo.apex.common.agent.AgentDefinitionRecoverySnapshot;
import org.gemo.apex.common.exception.InvalidSnapshotException;
import org.gemo.apex.common.exception.UnsupportedSnapshotVersionException;
import org.gemo.apex.common.execution.IterationStatus;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.execution.TurnStatus;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.snapshot.*;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.platform.persistence.session.AgentSessionEntity;

public final class SessionSnapshotTextAdapterV1 {
    public AgentSessionEntity encode(SessionSnapshot snapshot) {
        RuntimeState state =
                new RuntimeState(
                        snapshot.schemaVersion(),
                        snapshot.historicalToolBindings(),
                        encodeTurn(snapshot),
                        snapshot.suspendedToolBatch(),
                        snapshot.nextMessageSortNo());
        return new AgentSessionEntity(
                snapshot.sessionId(),
                snapshot.userId(),
                snapshot.agentKey(),
                snapshot.status().name(),
                snapshot.currentTurnNo(),
                JsonUtils.toJson(snapshot.activeDefinition()),
                JsonUtils.toJson(snapshot.enabledTools()),
                JsonUtils.toJson(snapshot.activatedSkills()),
                JsonUtils.toJson(state),
                null,
                snapshot.lastActiveTime());
    }

    public SessionSnapshot decode(AgentSessionEntity entity) {
        RuntimeState state = JsonUtils.fromJson(entity.runtimeSnapshot(), RuntimeState.class);
        if (state == null || !SnapshotSchemaVersion.V1.equals(state.schemaVersion())) {
            throw new UnsupportedSnapshotVersionException(
                    state == null ? null : state.schemaVersion());
        }
        Set<String> enabled =
                JsonUtils.fromJson(entity.enabledToolNames(), new TypeReference<>() {});
        Set<String> activated =
                JsonUtils.fromJson(entity.activatedSkillNames(), new TypeReference<>() {});
        AgentDefinitionRecoverySnapshot definition =
                JsonUtils.fromJson(
                        entity.agentDefinitionSnapshot(), AgentDefinitionRecoverySnapshot.class);
        SuspendedToolBatch suspended = state.suspendedToolBatch();
        if (suspended == null) {
            suspended = decodeLegacySuspendedBatch(entity.suspendedToolCall());
        }
        TurnSnapshot activeTurn = decodeTurn(state.activeTurn(), suspended);
        return new SessionSnapshot(
                state.schemaVersion(),
                entity.sessionId(),
                entity.userId(),
                entity.agentKey(),
                SessionStatus.valueOf(entity.status()),
                entity.currentTurnNo(),
                enabled,
                activated,
                state.historicalToolBindings(),
                definition,
                activeTurn,
                suspended,
                state.nextMessageSortNo(),
                entity.lastActiveTime());
    }

    private PersistedTurn encodeTurn(SessionSnapshot snapshot) {
        TurnSnapshot turn = snapshot.activeTurn();
        IterationSnapshot iteration = turn.currentIteration();
        PersistedIteration persistedIteration = null;
        if (iteration != null) {
            List<ToolResult> completedToolResults =
                    snapshot.status() == SessionStatus.HUMAN_IN_THE_LOOP
                            ? iteration.completedToolResults()
                            : List.of();
            persistedIteration =
                    new PersistedIteration(
                            iteration.iterationNo(),
                            iteration.status(),
                            completedToolResults,
                            iteration.startedTime(),
                            iteration.endedTime());
        }
        return new PersistedTurn(
                turn.turnNo(),
                turn.status(),
                persistedIteration,
                turn.startedTime(),
                turn.endedTime());
    }

    private TurnSnapshot decodeTurn(PersistedTurn turn, SuspendedToolBatch suspended) {
        PersistedIteration persisted = turn.currentIteration();
        IterationSnapshot iteration = null;
        if (persisted != null) {
            ModelResponse response = suspended == null ? null : minimalResponse(suspended);
            iteration =
                    new IterationSnapshot(
                            persisted.iterationNo(),
                            persisted.status(),
                            null,
                            response,
                            persisted.completedToolResults(),
                            persisted.startedTime(),
                            persisted.endedTime());
        }
        return new TurnSnapshot(
                turn.turnNo(), turn.status(), iteration, turn.startedTime(), turn.endedTime());
    }

    private ModelResponse minimalResponse(SuspendedToolBatch suspended) {
        List<ToolCall> calls =
                suspended.toolCalls().stream()
                        .map(
                                prepared ->
                                        new ToolCall(
                                                prepared.toolCallId(),
                                                prepared.toolName(),
                                                prepared.ordinal(),
                                                prepared.resolvedArguments(),
                                                prepared.toolCallMetadata()))
                        .toList();
        return new ModelResponse(null, calls, Map.of());
    }

    private SuspendedToolBatch decodeLegacySuspendedBatch(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        var tree = JsonUtils.parseTree(json);
        if (tree == null || !tree.has("toolCalls")) {
            throw new InvalidSnapshotException("不支持旧版单条挂起快照，请先清理 HUMAN_IN_THE_LOOP 会话");
        }
        return JsonUtils.fromJson(json, SuspendedToolBatch.class);
    }

    public record RuntimeState(
            String schemaVersion,
            List<HistoricalToolBinding> historicalToolBindings,
            PersistedTurn activeTurn,
            SuspendedToolBatch suspendedToolBatch,
            long nextMessageSortNo) {}

    public record PersistedTurn(
            long turnNo,
            TurnStatus status,
            PersistedIteration currentIteration,
            Instant startedTime,
            Instant endedTime) {}

    public record PersistedIteration(
            int iterationNo,
            IterationStatus status,
            List<ToolResult> completedToolResults,
            Instant startedTime,
            Instant endedTime) {}
}
