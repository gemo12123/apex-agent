package org.gemo.apex.core.agent;

import org.gemo.apex.common.agent.AgentDefinitionRecoverySnapshot;
import org.gemo.apex.common.agent.AgentDefinitionSnapshot;
import org.gemo.apex.common.conversation.ConversationCompactionRequest;
import org.gemo.apex.common.conversation.ConversationCompactionResult;
import org.gemo.apex.common.execution.IterationStatus;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.execution.TurnStatus;
import org.gemo.apex.common.intervention.HumanSubmission;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.snapshot.*;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolOrigin;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.common.tool.UnavailableToolSource;
import org.gemo.apex.core.tool.ToolCatalog;

import java.time.Instant;
import java.util.*;

/** 请求内唯一可变聚合；Hook 只能看到由它构造的只读 common Context。 */
public final class ApexAgentContext {
    private final AgentPorts ports;
    private final AgentDefinitionSnapshot definition;
    private final ToolCatalog toolCatalog;
    private SessionSnapshot snapshot;
    private ModelRequest modelRequest;
    private ModelResponse modelResponse;
    private ToolCall toolCall;
    private ToolResult toolResult;
    private ConversationCompactionRequest compactionRequest;
    private ConversationCompactionResult compactionResult;
    private final HumanSubmission humanSubmission;

    ApexAgentContext(AgentPorts ports, AgentDefinitionSnapshot definition,
                     ToolCatalog toolCatalog, SessionSnapshot snapshot,
                     HumanSubmission humanSubmission) {
        this.ports = ports;
        this.definition = definition;
        this.toolCatalog = toolCatalog;
        this.snapshot = snapshot;
        this.humanSubmission = humanSubmission;
    }

    public AgentPorts ports() { return ports; }
    public AgentDefinitionSnapshot definition() { return definition; }
    public ToolCatalog toolCatalog() { return toolCatalog; }
    public SessionSnapshot snapshot() { return snapshot; }
    public ModelRequest modelRequest() { return modelRequest; }
    public void modelRequest(ModelRequest value) { modelRequest = value; }
    public ModelResponse modelResponse() { return modelResponse; }
    public void modelResponse(ModelResponse value) { modelResponse = value; }
    public ToolCall toolCall() { return toolCall; }
    public void toolCall(ToolCall value) { toolCall = value; }
    public ToolResult toolResult() { return toolResult; }
    public void toolResult(ToolResult value) { toolResult = value; }
    public ConversationCompactionRequest compactionRequest() { return compactionRequest; }
    public void compactionRequest(ConversationCompactionRequest value) { compactionRequest = value; }
    public ConversationCompactionResult compactionResult() { return compactionResult; }
    public void compactionResult(ConversationCompactionResult value) { compactionResult = value; }
    public HumanSubmission humanSubmission() { return humanSubmission; }
    public HumanSubmission currentHumanSubmission() {
        return humanSubmission != null && toolCall != null
                && humanSubmission.toolCallId().equals(toolCall.toolCallId()) ? humanSubmission : null;
    }

    public void enableTools(Set<String> enable, Set<String> disable) {
        Set<String> next = new LinkedHashSet<>(snapshot.enabledTools());
        if (!definition.definition().tools().availableTools().containsAll(enable)) {
            throw new IllegalArgumentException("Hook 不能启用 availableTools 之外的工具");
        }
        next.addAll(enable);
        next.removeAll(disable);
        replaceSnapshot(snapshot.status(), next, snapshot.activeTurn(), snapshot.suspendedToolCall(),
                snapshot.lastActiveTime());
    }

    public void startIteration(int iterationNo) {
        Instant now = ports.timeProvider().now();
        IterationSnapshot iteration = new IterationSnapshot(iterationNo, IterationStatus.IN_PROGRESS,
                null, null, List.of(), now, null);
        TurnSnapshot turn = new TurnSnapshot(snapshot.currentTurnNo(), TurnStatus.IN_PROGRESS,
                iteration, snapshot.activeTurn().startedTime(), null);
        replaceSnapshot(SessionStatus.IN_PROGRESS, snapshot.enabledTools(), turn, null, now);
    }

    public void updateIteration(ModelRequest request, ModelResponse response, List<ToolResult> results,
                                IterationStatus status, Instant endedTime) {
        IterationSnapshot old = snapshot.activeTurn().currentIteration();
        IterationSnapshot iteration = new IterationSnapshot(old.iterationNo(), status, request, response,
                results, old.startedTime(), endedTime);
        TurnSnapshot turn = new TurnSnapshot(snapshot.currentTurnNo(), snapshot.activeTurn().status(),
                iteration, snapshot.activeTurn().startedTime(), snapshot.activeTurn().endedTime());
        replaceSnapshot(snapshot.status(), snapshot.enabledTools(), turn, snapshot.suspendedToolCall(),
                ports.timeProvider().now());
    }

    public void completeIteration() {
        IterationSnapshot old = snapshot.activeTurn().currentIteration();
        updateIteration(old.modelRequest(), old.modelResponse(), old.completedToolResults(),
                IterationStatus.COMPLETED, ports.timeProvider().now());
    }

    public void addToolResults(List<ToolResult> results) {
        IterationSnapshot old = snapshot.activeTurn().currentIteration();
        List<ToolResult> all = new ArrayList<>(old.completedToolResults());
        all.addAll(results);
        updateIteration(old.modelRequest(), old.modelResponse(), all, old.status(), old.endedTime());
    }

    public void suspend(SuspendedToolCall suspended, boolean replaceExisting) {
        if (!replaceExisting && snapshot.suspendedToolCall() != null) {
            throw new IllegalStateException("当前会话已经存在挂起工具调用");
        }
        Instant now = ports.timeProvider().now();
        TurnSnapshot oldTurn = snapshot.activeTurn();
        IterationSnapshot oldIteration = oldTurn.currentIteration();
        IterationSnapshot iteration = new IterationSnapshot(oldIteration.iterationNo(), IterationStatus.SUSPENDED,
                oldIteration.modelRequest(), oldIteration.modelResponse(), oldIteration.completedToolResults(),
                oldIteration.startedTime(), null);
        TurnSnapshot turn = new TurnSnapshot(oldTurn.turnNo(), TurnStatus.SUSPENDED, iteration,
                oldTurn.startedTime(), null);
        replaceSnapshot(SessionStatus.HUMAN_IN_THE_LOOP, snapshot.enabledTools(), turn, suspended, now);
    }

    public void resumeFromSuspension() {
        Instant now = ports.timeProvider().now();
        TurnSnapshot oldTurn = snapshot.activeTurn();
        IterationSnapshot oldIteration = oldTurn.currentIteration();
        IterationSnapshot iteration = new IterationSnapshot(oldIteration.iterationNo(), IterationStatus.IN_PROGRESS,
                oldIteration.modelRequest(), oldIteration.modelResponse(), oldIteration.completedToolResults(),
                oldIteration.startedTime(), null);
        TurnSnapshot turn = new TurnSnapshot(oldTurn.turnNo(), TurnStatus.IN_PROGRESS, iteration,
                oldTurn.startedTime(), null);
        replaceSnapshot(SessionStatus.IN_PROGRESS, snapshot.enabledTools(), turn, null, now);
    }

    public void migrateUnavailableTool(String toolName) {
        var availability = ports.toolAvailabilityProvider().current();
        UnavailableToolSource source = availability.unavailableSources().stream()
                .filter(item -> toolName.startsWith(item.stableNamePrefix())).findFirst().orElse(null);
        ToolOrigin origin = source == null ? ToolOrigin.LOCAL : source.origin();
        String sourceId = source == null ? toolName : source.sourceId();
        String reason = source == null ? "UNAVAILABLE" : source.reasonCode();
        HistoricalToolBinding addition = new HistoricalToolBinding(toolName, origin, sourceId, reason,
                ports.timeProvider().now());
        List<HistoricalToolBinding> history = new ArrayList<>(snapshot.historicalToolBindings());
        if (history.stream().noneMatch(item -> item.identity().equals(addition.identity()))) history.add(addition);
        Set<String> enabled = new LinkedHashSet<>(snapshot.enabledTools());
        enabled.remove(toolName);
        snapshot = new SessionSnapshot(snapshot.schemaVersion(), snapshot.sessionId(), snapshot.userId(),
                snapshot.agentKey(), snapshot.status(), snapshot.currentTurnNo(), enabled,
                snapshot.activatedSkills(), history, snapshot.activeDefinition(), snapshot.activeTurn(),
                snapshot.suspendedToolCall(), snapshot.nextMessageSortNo(), ports.timeProvider().now());
    }

    public void completeTurn(boolean endedByHook) {
        Instant now = ports.timeProvider().now();
        TurnSnapshot old = snapshot.activeTurn();
        TurnSnapshot turn = new TurnSnapshot(old.turnNo(),
                endedByHook ? TurnStatus.ENDED_BY_HOOK : TurnStatus.COMPLETED,
                old.currentIteration(), old.startedTime(), now);
        replaceSnapshot(SessionStatus.COMPLETED, snapshot.enabledTools(), turn, null, now);
    }

    public void fail() { transitionTerminal(SessionStatus.FAILED, TurnStatus.FAILED, IterationStatus.FAILED); }
    public void cancel() { transitionTerminal(SessionStatus.CANCELLED, TurnStatus.CANCELLED, IterationStatus.CANCELLED); }

    private void transitionTerminal(SessionStatus sessionStatus, TurnStatus turnStatus,
                                    IterationStatus iterationStatus) {
        Instant now = ports.timeProvider().now();
        TurnSnapshot oldTurn = snapshot.activeTurn();
        IterationSnapshot oldIteration = oldTurn.currentIteration();
        IterationSnapshot iteration = oldIteration == null ? null : new IterationSnapshot(
                oldIteration.iterationNo(), iterationStatus, oldIteration.modelRequest(),
                oldIteration.modelResponse(), oldIteration.completedToolResults(),
                oldIteration.startedTime(), now);
        TurnSnapshot turn = new TurnSnapshot(oldTurn.turnNo(), turnStatus, iteration,
                oldTurn.startedTime(), now);
        replaceSnapshot(sessionStatus, snapshot.enabledTools(), turn, null, now);
    }

    public void save() {
        ports.cancellationToken().throwIfCancellationRequested();
        ports.sessionRepository().save(snapshot);
        ports.cancellationToken().throwIfCancellationRequested();
    }

    public long allocateSortNo() {
        long value = snapshot.nextMessageSortNo();
        snapshot = new SessionSnapshot(snapshot.schemaVersion(), snapshot.sessionId(), snapshot.userId(),
                snapshot.agentKey(), snapshot.status(), snapshot.currentTurnNo(), snapshot.enabledTools(),
                snapshot.activatedSkills(), snapshot.historicalToolBindings(), snapshot.activeDefinition(),
                snapshot.activeTurn(), snapshot.suspendedToolCall(), value + 1, snapshot.lastActiveTime());
        return value;
    }

    private void replaceSnapshot(SessionStatus status, Set<String> enabledTools, TurnSnapshot turn,
                                 SuspendedToolCall suspended, Instant activeTime) {
        snapshot = new SessionSnapshot(snapshot.schemaVersion(), snapshot.sessionId(), snapshot.userId(),
                snapshot.agentKey(), status, snapshot.currentTurnNo(), enabledTools,
                snapshot.activatedSkills(), snapshot.historicalToolBindings(), snapshot.activeDefinition(),
                turn, suspended, snapshot.nextMessageSortNo(), activeTime);
    }

    static AgentDefinitionRecoverySnapshot recovery(AgentDefinitionSnapshot snapshot) {
        var definition = snapshot.definition();
        return new AgentDefinitionRecoverySnapshot(definition.schemaVersion(), definition.metadata(),
                definition.prompt(), definition.messageCompression(), definition.tools().availableTools(),
                definition.enabledSkills(), definition.subAgents(), definition.hooks());
    }
}
