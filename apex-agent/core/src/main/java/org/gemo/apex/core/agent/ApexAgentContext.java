package org.gemo.apex.core.agent;

import java.time.Instant;
import java.util.*;
import org.gemo.apex.common.agent.AgentDefinitionRecoverySnapshot;
import org.gemo.apex.common.agent.AgentDefinitionSnapshot;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.execution.IterationStatus;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.execution.TurnStatus;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.operation.AppendMessage;
import org.gemo.apex.common.hook.operation.MessageOperation;
import org.gemo.apex.common.hook.operation.RemoveMessage;
import org.gemo.apex.common.hook.operation.ReplaceMessage;
import org.gemo.apex.common.hook.operation.SkillActivationDelta;
import org.gemo.apex.common.intervention.HumanSubmission;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.shared.SharedDataCleanupPolicy;
import org.gemo.apex.common.shared.SharedDataEntry;
import org.gemo.apex.common.shared.SharedDataStore;
import org.gemo.apex.common.snapshot.*;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolOrigin;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.common.tool.UnavailableToolSource;
import org.gemo.apex.core.tool.ToolCatalog;

/** 请求内唯一可变聚合；Hook 只能看到由它构造的只读 common Context。 */
public final class ApexAgentContext {
    private final AgentPorts ports;
    private final AgentDefinitionSnapshot definition;
    private final ToolCatalog toolCatalog;
    private SessionSnapshot snapshot;
    private ConversationWindow conversationWindow;
    private ModelRequest modelRequest;
    private ModelResponse modelResponse;
    private ToolCall toolCall;
    private ToolResult toolResult;
    private ConversationCompactionRequest compactionRequest;
    private ConversationCompactionResult compactionResult;
    private final List<MessageOperation> pendingPostCompressionOperations = new ArrayList<>();
    private Set<String> pendingActivatedSkills;
    private final Map<String, Object> humanResponses;
    private final SharedDataStore sharedData;
    private HumanSubmission humanSubmission;

    ApexAgentContext(
            AgentPorts ports,
            AgentDefinitionSnapshot definition,
            ToolCatalog toolCatalog,
            SessionSnapshot snapshot,
            ConversationWindow conversationWindow,
            Map<String, Object> humanResponses,
            SharedDataStore sharedData) {
        this.ports = ports;
        this.definition = definition;
        this.toolCatalog = toolCatalog;
        this.snapshot = snapshot;
        if (!snapshot.sessionId().equals(conversationWindow.sessionId())) {
            throw new IllegalArgumentException("ConversationWindow 必须属于当前 Session");
        }
        this.conversationWindow = conversationWindow;
        this.humanResponses = humanResponses == null ? null : Map.copyOf(humanResponses);
        this.sharedData = Objects.requireNonNull(sharedData, "sharedData");
    }

    public AgentPorts ports() {
        return ports;
    }

    public AgentDefinitionSnapshot definition() {
        return definition;
    }

    public ToolCatalog toolCatalog() {
        return toolCatalog;
    }

    public SessionSnapshot snapshot() {
        syncSharedDataSnapshot();
        return snapshot;
    }

    public SharedDataStore sharedData() {
        return sharedData;
    }

    void cleanupSharedData(SharedDataCleanupPolicy cleanupPolicy) {
        sharedData.entries().entrySet().stream()
                .filter(entry -> entry.getValue().cleanupPolicy() == cleanupPolicy)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(sharedData::remove);
    }

    public ConversationWindow conversationWindow() {
        return conversationWindow;
    }

    /** 预先构造下一窗口，Repository 成功后再替换内存状态。 */
    public void appendConversation(List<AgentMessageEntry> entries) {
        ConversationAppend append = prepareAppend(entries);
        if (append.newEntries().isEmpty()) {
            return;
        }
        ports.conversationRepository()
                .commit(
                        new ConversationWriteBatch(
                                snapshot.sessionId(),
                                append.newEntries().stream()
                                        .map(AppendConversationWrite::new)
                                        .map(ConversationWrite.class::cast)
                                        .toList()));
        conversationWindow = append.window();
    }

    /** 压缩提交成功后，将有效窗口同步切换为累计摘要与保留尾部。 */
    public void compactConversation(ConversationCompactionCommit commit) {
        ConversationWindow next = prepareCompactedWindow(commit);
        ports.conversationRepository()
                .commit(
                        new ConversationWriteBatch(
                                snapshot.sessionId(),
                                List.of(new CompactConversationWrite(commit))));
        conversationWindow = next;
    }

    /** 原子应用 Hook 声明的持久化消息操作，Repository 成功后才替换内存窗口。 */
    public void mutateConversation(List<MessageOperation> operations) {
        if (operations.isEmpty()) {
            return;
        }
        ConversationMutation mutation = prepareMutation(conversationWindow, operations);
        ports.conversationRepository()
                .commit(new ConversationWriteBatch(snapshot.sessionId(), mutation.writes()));
        conversationWindow = mutation.window();
    }

    /** 将压缩与 POST_MESSAGE_COMPRESSION 声明的消息操作放入同一原子提交。 */
    public void compactConversation(
            ConversationCompactionCommit commit, List<MessageOperation> operations) {
        ConversationWindow compacted = prepareCompactedWindow(commit);
        ConversationMutation mutation = prepareMutation(compacted, operations);
        List<ConversationWrite> writes = new ArrayList<>();
        writes.add(new CompactConversationWrite(commit));
        writes.addAll(mutation.writes());
        ports.conversationRepository()
                .commit(new ConversationWriteBatch(snapshot.sessionId(), writes));
        conversationWindow = mutation.window();
    }

    public ModelRequest modelRequest() {
        return modelRequest;
    }

    public void modelRequest(ModelRequest value) {
        modelRequest = value;
    }

    public ModelResponse modelResponse() {
        return modelResponse;
    }

    public void modelResponse(ModelResponse value) {
        modelResponse = value;
    }

    public ToolCall toolCall() {
        return toolCall;
    }

    public void toolCall(ToolCall value) {
        toolCall = value;
    }

    public ToolResult toolResult() {
        return toolResult;
    }

    public void toolResult(ToolResult value) {
        toolResult = value;
    }

    public ConversationCompactionRequest compactionRequest() {
        return compactionRequest;
    }

    public void compactionRequest(ConversationCompactionRequest value) {
        compactionRequest = value;
    }

    public ConversationCompactionResult compactionResult() {
        return compactionResult;
    }

    public void compactionResult(ConversationCompactionResult value) {
        compactionResult = value;
    }

    public void resetPostCompressionOperations() {
        pendingPostCompressionOperations.clear();
    }

    public void stagePostCompressionOperations(List<MessageOperation> operations) {
        pendingPostCompressionOperations.addAll(List.copyOf(operations));
    }

    public List<MessageOperation> drainPostCompressionOperations() {
        List<MessageOperation> operations = List.copyOf(pendingPostCompressionOperations);
        pendingPostCompressionOperations.clear();
        return operations;
    }

    public boolean resumedRequest() {
        return humanResponses != null;
    }

    public Map<String, Object> humanResponses() {
        return humanResponses == null ? Map.of() : humanResponses;
    }

    public HumanSubmission humanSubmission() {
        return humanSubmission;
    }

    public void humanSubmission(HumanSubmission value) {
        humanSubmission = value;
    }

    public HumanSubmission currentHumanSubmission() {
        return humanSubmission != null
                        && toolCall != null
                        && humanSubmission.toolCallId().equals(toolCall.toolCallId())
                ? humanSubmission
                : null;
    }

    public void enableTools(Set<String> enable, Set<String> disable) {
        Set<String> next = new LinkedHashSet<>(snapshot.enabledTools());
        if (!definition.definition().tools().availableTools().containsAll(enable)) {
            throw new IllegalArgumentException("Hook 不能启用 availableTools 之外的工具");
        }
        next.addAll(enable);
        next.removeAll(disable);
        replaceSnapshot(
                snapshot.status(),
                next,
                snapshot.activeTurn(),
                snapshot.suspendedToolBatch(),
                snapshot.lastActiveTime());
    }

    public void startIteration(int iterationNo) {
        Instant now = ports.timeProvider().now();
        IterationSnapshot iteration =
                new IterationSnapshot(
                        iterationNo, IterationStatus.IN_PROGRESS, null, null, List.of(), now, null);
        TurnSnapshot turn =
                new TurnSnapshot(
                        snapshot.currentTurnNo(),
                        TurnStatus.IN_PROGRESS,
                        iteration,
                        snapshot.activeTurn().startedTime(),
                        null);
        replaceSnapshot(SessionStatus.IN_PROGRESS, snapshot.enabledTools(), turn, null, now);
    }

    public void updateIteration(
            ModelRequest request,
            ModelResponse response,
            List<ToolResult> results,
            IterationStatus status,
            Instant endedTime) {
        IterationSnapshot old = snapshot.activeTurn().currentIteration();
        IterationSnapshot iteration =
                new IterationSnapshot(
                        old.iterationNo(),
                        status,
                        request == null ? null : request.withoutPrefixDeveloperMessages(),
                        response,
                        results,
                        old.startedTime(),
                        endedTime);
        TurnSnapshot turn =
                new TurnSnapshot(
                        snapshot.currentTurnNo(),
                        snapshot.activeTurn().status(),
                        iteration,
                        snapshot.activeTurn().startedTime(),
                        snapshot.activeTurn().endedTime());
        replaceSnapshot(
                snapshot.status(),
                snapshot.enabledTools(),
                turn,
                snapshot.suspendedToolBatch(),
                ports.timeProvider().now());
    }

    public void completeIteration() {
        IterationSnapshot old = snapshot.activeTurn().currentIteration();
        updateIteration(
                old.modelRequest(),
                old.modelResponse(),
                old.completedToolResults(),
                IterationStatus.COMPLETED,
                ports.timeProvider().now());
    }

    public void addToolResults(List<ToolResult> results) {
        IterationSnapshot old = snapshot.activeTurn().currentIteration();
        List<ToolResult> all = new ArrayList<>(old.completedToolResults());
        all.addAll(results);
        updateIteration(
                old.modelRequest(), old.modelResponse(), all, old.status(), old.endedTime());
    }

    public void suspend(SuspendedToolBatch suspended, boolean replaceExisting) {
        if (!replaceExisting && snapshot.suspendedToolBatch() != null) {
            throw new IllegalStateException("当前会话已经存在挂起工具批次");
        }
        Instant now = ports.timeProvider().now();
        TurnSnapshot oldTurn = snapshot.activeTurn();
        IterationSnapshot oldIteration = oldTurn.currentIteration();
        IterationSnapshot iteration =
                new IterationSnapshot(
                        oldIteration.iterationNo(),
                        IterationStatus.SUSPENDED,
                        oldIteration.modelRequest(),
                        oldIteration.modelResponse(),
                        oldIteration.completedToolResults(),
                        oldIteration.startedTime(),
                        null);
        TurnSnapshot turn =
                new TurnSnapshot(
                        oldTurn.turnNo(),
                        TurnStatus.SUSPENDED,
                        iteration,
                        oldTurn.startedTime(),
                        null);
        replaceSnapshot(
                SessionStatus.HUMAN_IN_THE_LOOP, snapshot.enabledTools(), turn, suspended, now);
    }

    public void resumeFromSuspension() {
        Instant now = ports.timeProvider().now();
        TurnSnapshot oldTurn = snapshot.activeTurn();
        IterationSnapshot oldIteration = oldTurn.currentIteration();
        IterationSnapshot iteration =
                new IterationSnapshot(
                        oldIteration.iterationNo(),
                        IterationStatus.IN_PROGRESS,
                        oldIteration.modelRequest(),
                        oldIteration.modelResponse(),
                        oldIteration.completedToolResults(),
                        oldIteration.startedTime(),
                        null);
        TurnSnapshot turn =
                new TurnSnapshot(
                        oldTurn.turnNo(),
                        TurnStatus.IN_PROGRESS,
                        iteration,
                        oldTurn.startedTime(),
                        null);
        replaceSnapshot(SessionStatus.IN_PROGRESS, snapshot.enabledTools(), turn, null, now);
    }

    public void migrateUnavailableTool(String toolName) {
        var availability = ports.toolAvailabilityProvider().current();
        UnavailableToolSource source =
                availability.unavailableSources().stream()
                        .filter(item -> toolName.startsWith(item.stableNamePrefix()))
                        .findFirst()
                        .orElse(null);
        ToolOrigin origin = source == null ? ToolOrigin.LOCAL : source.origin();
        String sourceId = source == null ? toolName : source.sourceId();
        String reason = source == null ? "UNAVAILABLE" : source.reasonCode();
        HistoricalToolBinding addition =
                new HistoricalToolBinding(
                        toolName, origin, sourceId, reason, ports.timeProvider().now());
        List<HistoricalToolBinding> history = new ArrayList<>(snapshot.historicalToolBindings());
        if (history.stream().noneMatch(item -> item.identity().equals(addition.identity()))) {
            history.add(addition);
        }
        Set<String> enabled = new LinkedHashSet<>(snapshot.enabledTools());
        enabled.remove(toolName);
        snapshot =
                new SessionSnapshot(
                        snapshot.schemaVersion(),
                        snapshot.sessionId(),
                        snapshot.userId(),
                        snapshot.agentKey(),
                        snapshot.status(),
                        snapshot.currentTurnNo(),
                        enabled,
                        snapshot.activatedSkills(),
                        history,
                        snapshot.activeDefinition(),
                        snapshot.activeTurn(),
                        snapshot.suspendedToolBatch(),
                        sharedData.entries(),
                        snapshot.executionErrors(),
                        snapshot.nextMessageSortNo(),
                        ports.timeProvider().now());
    }

    public void stageSkillActivation(SkillActivationDelta delta) {
        if (delta.activate().isEmpty() && delta.deactivate().isEmpty()) {
            return;
        }
        if (!definition.definition().enabledSkills().containsAll(delta.activate())) {
            throw new IllegalArgumentException("待激活 Skill 必须属于 enabledSkills");
        }
        Set<String> next =
                new LinkedHashSet<>(
                        pendingActivatedSkills == null
                                ? snapshot.activatedSkills()
                                : pendingActivatedSkills);
        next.addAll(delta.activate());
        next.removeAll(delta.deactivate());
        pendingActivatedSkills = Set.copyOf(next);
    }

    public void applyPendingSkillActivation() {
        if (pendingActivatedSkills == null) {
            return;
        }
        snapshot =
                new SessionSnapshot(
                        snapshot.schemaVersion(),
                        snapshot.sessionId(),
                        snapshot.userId(),
                        snapshot.agentKey(),
                        snapshot.status(),
                        snapshot.currentTurnNo(),
                        snapshot.enabledTools(),
                        pendingActivatedSkills,
                        snapshot.historicalToolBindings(),
                        snapshot.activeDefinition(),
                        snapshot.activeTurn(),
                        snapshot.suspendedToolBatch(),
                        sharedData.entries(),
                        snapshot.executionErrors(),
                        snapshot.nextMessageSortNo(),
                        ports.timeProvider().now());
        pendingActivatedSkills = null;
    }

    public void completeTurn(boolean endedByHook) {
        Instant now = ports.timeProvider().now();
        TurnSnapshot old = snapshot.activeTurn();
        TurnSnapshot turn =
                new TurnSnapshot(
                        old.turnNo(),
                        endedByHook ? TurnStatus.ENDED_BY_HOOK : TurnStatus.COMPLETED,
                        old.currentIteration(),
                        old.startedTime(),
                        now);
        replaceSnapshot(SessionStatus.COMPLETED, snapshot.enabledTools(), turn, null, now);
    }

    public void fail() {
        transitionTerminal(SessionStatus.FAILED, TurnStatus.FAILED, IterationStatus.FAILED);
    }

    public void cancel() {
        transitionTerminal(
                SessionStatus.CANCELLED, TurnStatus.CANCELLED, IterationStatus.CANCELLED);
    }

    public void recordHookFailure(HookPoint point, String hookId) {
        IterationSnapshot iteration = snapshot.activeTurn().currentIteration();
        Integer iterationNo = iteration == null ? null : iteration.iterationNo();
        String location =
                iterationNo == null
                        ? "第 " + snapshot.currentTurnNo() + " 个 Turn"
                        : "第 " + snapshot.currentTurnNo() + " 个 Turn 第 " + iterationNo + " 轮";
        recordExecutionError(
                new ExecutionErrorSnapshot(
                        snapshot.currentTurnNo(),
                        iterationNo,
                        ExecutionErrorType.HOOK,
                        point,
                        hookId,
                        location + " 的 " + point + " Hook " + hookId + " 执行失败",
                        ports.timeProvider().now()));
    }

    public void recordModelFailure(RuntimeException error) {
        IterationSnapshot iteration = snapshot.activeTurn().currentIteration();
        String message = error.getMessage();
        recordExecutionError(
                new ExecutionErrorSnapshot(
                        snapshot.currentTurnNo(),
                        iteration == null ? null : iteration.iterationNo(),
                        ExecutionErrorType.MODEL,
                        null,
                        null,
                        message == null || message.isBlank()
                                ? error.getClass().getSimpleName()
                                : message,
                        ports.timeProvider().now()));
    }

    private void transitionTerminal(
            SessionStatus sessionStatus, TurnStatus turnStatus, IterationStatus iterationStatus) {
        Instant now = ports.timeProvider().now();
        TurnSnapshot oldTurn = snapshot.activeTurn();
        IterationSnapshot oldIteration = oldTurn.currentIteration();
        IterationSnapshot iteration =
                oldIteration == null
                        ? null
                        : new IterationSnapshot(
                                oldIteration.iterationNo(),
                                iterationStatus,
                                oldIteration.modelRequest(),
                                oldIteration.modelResponse(),
                                oldIteration.completedToolResults(),
                                oldIteration.startedTime(),
                                now);
        TurnSnapshot turn =
                new TurnSnapshot(
                        oldTurn.turnNo(), turnStatus, iteration, oldTurn.startedTime(), now);
        replaceSnapshot(sessionStatus, snapshot.enabledTools(), turn, null, now);
    }

    public void save() {
        ports.cancellationToken().throwIfCancellationRequested();
        saveWithoutCancellationCheck();
        ports.cancellationToken().throwIfCancellationRequested();
    }

    public void saveWithoutCancellationCheck() {
        ports.sessionRepository().save(snapshot());
    }

    public long allocateSortNo() {
        long value = snapshot.nextMessageSortNo();
        snapshot =
                new SessionSnapshot(
                        snapshot.schemaVersion(),
                        snapshot.sessionId(),
                        snapshot.userId(),
                        snapshot.agentKey(),
                        snapshot.status(),
                        snapshot.currentTurnNo(),
                        snapshot.enabledTools(),
                        snapshot.activatedSkills(),
                        snapshot.historicalToolBindings(),
                        snapshot.activeDefinition(),
                        snapshot.activeTurn(),
                        snapshot.suspendedToolBatch(),
                        sharedData.entries(),
                        snapshot.executionErrors(),
                        value + 1,
                        snapshot.lastActiveTime());
        return value;
    }

    private ConversationAppend prepareAppend(List<AgentMessageEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        List<AgentMessageEntry> messages = new ArrayList<>(conversationWindow.messages());
        List<AgentMessageEntry> additions = new ArrayList<>();
        for (AgentMessageEntry entry : List.copyOf(entries)) {
            if (!snapshot.sessionId().equals(entry.sessionId())) {
                throw new IllegalArgumentException("追加消息必须属于当前 Session");
            }
            if (entry.messageType() == MessageType.SUMMARY) {
                throw new IllegalArgumentException("SUMMARY 不能作为普通消息追加");
            }
            ConversationSummary summary = conversationWindow.summary();
            if (summary != null && entry.sortNo() <= summary.sourceEndSortNo()) {
                throw new IllegalStateException("追加消息不能位于摘要覆盖范围内");
            }
            AgentMessageEntry sameId =
                    messages.stream()
                            .filter(current -> current.entryId().equals(entry.entryId()))
                            .findFirst()
                            .orElse(null);
            if (sameId != null) {
                if (!samePersistentMessage(sameId, entry)) {
                    throw new IllegalStateException("消息 entryId 冲突: " + entry.entryId());
                }
                continue;
            }
            if (messages.stream().anyMatch(current -> current.sortNo() == entry.sortNo())) {
                throw new IllegalStateException("消息 sortNo 冲突: " + entry.sortNo());
            }
            messages.add(entry);
            additions.add(entry);
        }
        messages.sort(Comparator.comparingLong(AgentMessageEntry::sortNo));
        return new ConversationAppend(window(conversationWindow.summary(), messages), additions);
    }

    private ConversationMutation prepareMutation(
            ConversationWindow source, List<MessageOperation> operations) {
        Objects.requireNonNull(operations, "operations");
        List<AgentMessageEntry> messages = new ArrayList<>(source.messages());
        List<ConversationWrite> writes = new ArrayList<>();
        for (MessageOperation operation : List.copyOf(operations)) {
            switch (operation) {
                case AppendMessage append -> {
                    AgentMessageEntry entry =
                            new AgentMessageEntry(
                                    ports.idGenerator().newEntryId(),
                                    snapshot.sessionId(),
                                    snapshot.currentTurnNo(),
                                    allocateSortNo(),
                                    append.role(),
                                    append.messageType(),
                                    append.content(),
                                    append.payload(),
                                    ports.timeProvider().now());
                    messages.add(entry);
                    writes.add(new AppendConversationWrite(entry));
                }
                case ReplaceMessage replace -> {
                    int index = editableMessageIndex(messages, replace.targetEntryId());
                    AgentMessageEntry old = messages.get(index);
                    AgentMessageEntry replacement =
                            new AgentMessageEntry(
                                    old.entryId(),
                                    old.sessionId(),
                                    old.turnNo(),
                                    old.sortNo(),
                                    replace.role(),
                                    replace.messageType(),
                                    replace.content(),
                                    replace.payload(),
                                    old.createdTime());
                    messages.set(index, replacement);
                    writes.add(
                            new ReplaceConversationWrite(
                                    replace.targetEntryId(),
                                    replace.role(),
                                    replace.messageType(),
                                    replace.content(),
                                    replace.payload()));
                }
                case RemoveMessage remove -> {
                    int index = editableMessageIndex(messages, remove.targetEntryId());
                    messages.remove(index);
                    writes.add(new RemoveConversationWrite(remove.targetEntryId()));
                }
            }
        }
        messages.sort(Comparator.comparingLong(AgentMessageEntry::sortNo));
        return new ConversationMutation(window(source.summary(), messages), List.copyOf(writes));
    }

    private int editableMessageIndex(List<AgentMessageEntry> messages, String entryId) {
        for (int index = 0; index < messages.size(); index++) {
            AgentMessageEntry message = messages.get(index);
            if (message.entryId().equals(entryId)) {
                if (message.messageType() == MessageType.SUMMARY) {
                    throw new IllegalArgumentException("SUMMARY 不可编辑: " + entryId);
                }
                return index;
            }
        }
        throw new IllegalArgumentException("消息不存在或已被压缩: " + entryId);
    }

    private ConversationWindow prepareCompactedWindow(ConversationCompactionCommit commit) {
        if (!snapshot.sessionId().equals(commit.sessionId())) {
            throw new IllegalArgumentException("压缩提交必须属于当前 Session");
        }
        List<AgentMessageEntry> messages = new ArrayList<>();
        messages.add(summaryMessage(commit.summary()));
        messages.addAll(commit.retainedMessages());
        messages.sort(Comparator.comparingLong(AgentMessageEntry::sortNo));
        return window(commit.summary(), messages);
    }

    private ConversationWindow window(
            ConversationSummary summary, List<AgentMessageEntry> messages) {
        return new ConversationWindow(
                snapshot.sessionId(),
                summary,
                messages,
                messages.isEmpty() ? null : messages.getFirst().sortNo(),
                messages.isEmpty() ? null : messages.getLast().sortNo());
    }

    private AgentMessageEntry summaryMessage(ConversationSummary summary) {
        return new AgentMessageEntry(
                "summary:" + summary.compactionId(),
                snapshot.sessionId(),
                summary.sourceTurnNo(),
                summary.sourceEndSortNo(),
                MessageRole.SYSTEM,
                MessageType.SUMMARY,
                summary.content(),
                Map.of(
                        "sourceStartSortNo", summary.sourceStartSortNo(),
                        "sourceEndSortNo", summary.sourceEndSortNo()),
                summary.updatedTime());
    }

    private boolean samePersistentMessage(AgentMessageEntry left, AgentMessageEntry right) {
        return left.entryId().equals(right.entryId())
                && left.sessionId().equals(right.sessionId())
                && left.turnNo() == right.turnNo()
                && left.sortNo() == right.sortNo()
                && left.role() == right.role()
                && left.messageType() == right.messageType()
                && Objects.equals(left.content(), right.content())
                && left.payload().equals(right.payload());
    }

    private record ConversationAppend(
            ConversationWindow window, List<AgentMessageEntry> newEntries) {
        private ConversationAppend {
            newEntries = List.copyOf(newEntries);
        }
    }

    private record ConversationMutation(
            ConversationWindow window, List<ConversationWrite> writes) {}

    private void replaceSnapshot(
            SessionStatus status,
            Set<String> enabledTools,
            TurnSnapshot turn,
            SuspendedToolBatch suspended,
            Instant activeTime) {
        snapshot =
                new SessionSnapshot(
                        snapshot.schemaVersion(),
                        snapshot.sessionId(),
                        snapshot.userId(),
                        snapshot.agentKey(),
                        status,
                        snapshot.currentTurnNo(),
                        enabledTools,
                        snapshot.activatedSkills(),
                        snapshot.historicalToolBindings(),
                        snapshot.activeDefinition(),
                        turn,
                        suspended,
                        sharedData.entries(),
                        snapshot.executionErrors(),
                        snapshot.nextMessageSortNo(),
                        activeTime);
    }

    private void syncSharedDataSnapshot() {
        Map<String, SharedDataEntry> current = sharedData.entries();
        if (snapshot.sharedData().equals(current)) {
            return;
        }
        snapshot =
                new SessionSnapshot(
                        snapshot.schemaVersion(),
                        snapshot.sessionId(),
                        snapshot.userId(),
                        snapshot.agentKey(),
                        snapshot.status(),
                        snapshot.currentTurnNo(),
                        snapshot.enabledTools(),
                        snapshot.activatedSkills(),
                        snapshot.historicalToolBindings(),
                        snapshot.activeDefinition(),
                        snapshot.activeTurn(),
                        snapshot.suspendedToolBatch(),
                        current,
                        snapshot.executionErrors(),
                        snapshot.nextMessageSortNo(),
                        snapshot.lastActiveTime());
    }

    private void recordExecutionError(ExecutionErrorSnapshot error) {
        List<ExecutionErrorSnapshot> errors = new ArrayList<>(snapshot.executionErrors());
        errors.add(error);
        snapshot =
                new SessionSnapshot(
                        snapshot.schemaVersion(),
                        snapshot.sessionId(),
                        snapshot.userId(),
                        snapshot.agentKey(),
                        snapshot.status(),
                        snapshot.currentTurnNo(),
                        snapshot.enabledTools(),
                        snapshot.activatedSkills(),
                        snapshot.historicalToolBindings(),
                        snapshot.activeDefinition(),
                        snapshot.activeTurn(),
                        snapshot.suspendedToolBatch(),
                        sharedData.entries(),
                        errors,
                        snapshot.nextMessageSortNo(),
                        ports.timeProvider().now());
    }

    static AgentDefinitionRecoverySnapshot recovery(AgentDefinitionSnapshot snapshot) {
        var definition = snapshot.definition();
        return new AgentDefinitionRecoverySnapshot(
                definition.schemaVersion(),
                definition.metadata(),
                definition.prompt(),
                definition.messageCompression(),
                definition.tools().availableTools(),
                definition.enabledSkills(),
                definition.subAgents(),
                definition.hooks());
    }
}
