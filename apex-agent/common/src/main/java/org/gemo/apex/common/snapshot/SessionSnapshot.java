package org.gemo.apex.common.snapshot;

import org.gemo.apex.common.agent.AgentDefinitionRecoverySnapshot;
import org.gemo.apex.common.exception.InvalidSnapshotException;
import org.gemo.apex.common.execution.SessionStatus;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.gemo.apex.common.support.DomainValues.*;

public record SessionSnapshot(String schemaVersion, String sessionId, String userId, String agentKey,
                              SessionStatus status, long currentTurnNo, Set<String> enabledTools,
                              Set<String> activatedSkills, List<HistoricalToolBinding> historicalToolBindings,
                              AgentDefinitionRecoverySnapshot activeDefinition, TurnSnapshot activeTurn,
                              SuspendedToolCall suspendedToolCall, long nextMessageSortNo,
                              Instant lastActiveTime) {
    public SessionSnapshot {
        if (!SnapshotSchemaVersion.V1.equals(schemaVersion)) {
            throw new InvalidSnapshotException("schemaVersion 必须为 " + SnapshotSchemaVersion.V1);
        }
        sessionId = required(sessionId, "sessionId");
        userId = required(userId, "userId");
        agentKey = required(agentKey, "agentKey");
        status = nonNull(status, "status");
        if (currentTurnNo < 1) throw new InvalidSnapshotException("currentTurnNo 必须大于 0");
        enabledTools = immutableNames(enabledTools, "enabledTools");
        activatedSkills = immutableNames(activatedSkills, "activatedSkills");
        historicalToolBindings = immutableList(historicalToolBindings, "historicalToolBindings");
        Set<String> historicalIdentities = new HashSet<>();
        for (HistoricalToolBinding binding : historicalToolBindings) {
            if (!historicalIdentities.add(binding.identity())) {
                throw new InvalidSnapshotException("historicalToolBindings 三元组不能重复");
            }
            if (enabledTools.contains(binding.toolName())) {
                throw new InvalidSnapshotException("历史工具不能出现在 enabledTools: " + binding.toolName());
            }
        }
        activeDefinition = nonNull(activeDefinition, "activeDefinition");
        activeTurn = nonNull(activeTurn, "activeTurn");
        if (activeTurn.turnNo() != currentTurnNo) {
            throw new InvalidSnapshotException("activeTurn.turnNo 与 currentTurnNo 不一致");
        }
        if (!activeDefinition.availableTools().containsAll(enabledTools)) {
            throw new InvalidSnapshotException("enabledTools 必须是 activeDefinition.availableTools 的子集");
        }
        if (!activeDefinition.enabledSkills().containsAll(activatedSkills)) {
            throw new InvalidSnapshotException("activatedSkills 必须是 activeDefinition.enabledSkills 的子集");
        }
        boolean suspended = suspendedToolCall != null;
        if (suspended != (status == SessionStatus.HUMAN_IN_THE_LOOP)) {
            throw new InvalidSnapshotException("suspendedToolCall 与 SessionStatus 不一致");
        }
        if (suspended && (activeTurn.status() != org.gemo.apex.common.execution.TurnStatus.SUSPENDED
                || activeTurn.currentIteration() == null
                || activeTurn.currentIteration().status()
                != org.gemo.apex.common.execution.IterationStatus.SUSPENDED)) {
            throw new InvalidSnapshotException("人工介入时 Turn 和 Iteration 必须为 SUSPENDED");
        }
        if (!suspended && activeTurn.status() == org.gemo.apex.common.execution.TurnStatus.SUSPENDED) {
            throw new InvalidSnapshotException("非人工介入快照不能保留 SUSPENDED Turn");
        }
        if (suspended && (!sessionId.equals(suspendedToolCall.sessionId())
                || currentTurnNo != suspendedToolCall.turnNo()
                || activeTurn.currentIteration() == null
                || activeTurn.currentIteration().iterationNo() != suspendedToolCall.iterationNo()
                || activeTurn.currentIteration().modelResponse() == null
                || activeTurn.currentIteration().modelResponse().toolCalls().stream()
                .noneMatch(call -> call.toolCallId().equals(suspendedToolCall.toolCallId())))) {
            throw new InvalidSnapshotException("suspendedToolCall 无法在活动 Turn/Iteration 中定位");
        }
        nonNegative(nextMessageSortNo, "nextMessageSortNo");
        lastActiveTime = nonNull(lastActiveTime, "lastActiveTime");
    }
}
