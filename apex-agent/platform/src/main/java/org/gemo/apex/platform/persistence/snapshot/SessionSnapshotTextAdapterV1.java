package org.gemo.apex.platform.persistence.snapshot;

import com.fasterxml.jackson.core.type.TypeReference;
import org.gemo.apex.common.agent.AgentDefinitionRecoverySnapshot;
import org.gemo.apex.common.exception.UnsupportedSnapshotVersionException;
import org.gemo.apex.common.exception.InvalidSnapshotException;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.snapshot.*;
import org.gemo.apex.platform.persistence.session.AgentSessionEntity;

import java.util.List;
import java.util.Set;

public final class SessionSnapshotTextAdapterV1 {
    public AgentSessionEntity encode(SessionSnapshot snapshot) {
        RuntimeState state = new RuntimeState(snapshot.schemaVersion(), snapshot.historicalToolBindings(),
                snapshot.activeTurn(), snapshot.nextMessageSortNo());
        return new AgentSessionEntity(snapshot.sessionId(), snapshot.userId(), snapshot.agentKey(),
                snapshot.status().name(), snapshot.currentTurnNo(), JsonUtils.toJson(snapshot.activeDefinition()),
                JsonUtils.toJson(snapshot.enabledTools()), JsonUtils.toJson(snapshot.activatedSkills()),
                JsonUtils.toJson(state), JsonUtils.toJson(snapshot.suspendedToolBatch()), snapshot.lastActiveTime());
    }

    public SessionSnapshot decode(AgentSessionEntity entity) {
        RuntimeState state = JsonUtils.fromJson(entity.runtimeSnapshot(), RuntimeState.class);
        if (state == null || !SnapshotSchemaVersion.V1.equals(state.schemaVersion())) {
            throw new UnsupportedSnapshotVersionException(state == null ? null : state.schemaVersion());
        }
        Set<String> enabled = JsonUtils.fromJson(entity.enabledToolNames(), new TypeReference<>() { });
        Set<String> activated = JsonUtils.fromJson(entity.activatedSkillNames(), new TypeReference<>() { });
        AgentDefinitionRecoverySnapshot definition = JsonUtils.fromJson(entity.agentDefinitionSnapshot(),
                AgentDefinitionRecoverySnapshot.class);
        SuspendedToolBatch suspended = decodeSuspendedBatch(entity.suspendedToolCall());
        return new SessionSnapshot(state.schemaVersion(), entity.sessionId(), entity.userId(), entity.agentKey(),
                SessionStatus.valueOf(entity.status()), entity.currentTurnNo(), enabled, activated,
                state.historicalToolBindings(), definition, state.activeTurn(), suspended,
                state.nextMessageSortNo(), entity.lastActiveTime());
    }

    private SuspendedToolBatch decodeSuspendedBatch(String json) {
        if (json == null || json.isBlank()) return null;
        var tree = JsonUtils.parseTree(json);
        if (tree == null || !tree.has("toolCalls")) {
            throw new InvalidSnapshotException("不支持旧版单条挂起快照，请先清理 HUMAN_IN_THE_LOOP 会话");
        }
        return JsonUtils.fromJson(json, SuspendedToolBatch.class);
    }

    public record RuntimeState(String schemaVersion, List<HistoricalToolBinding> historicalToolBindings,
                               TurnSnapshot activeTurn, long nextMessageSortNo) { }
}
