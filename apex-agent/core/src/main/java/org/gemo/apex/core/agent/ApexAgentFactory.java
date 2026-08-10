package org.gemo.apex.core.agent;

import java.util.*;
import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.execution.TurnStatus;
import org.gemo.apex.common.conversation.ConversationQuery;
import org.gemo.apex.common.conversation.ConversationWindow;
import org.gemo.apex.common.conversation.ConversationWindowRequest;
import org.gemo.apex.common.intervention.HumanResponseCommand;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.snapshot.*;
import org.gemo.apex.core.definition.AgentDefinitionAssembler;
import org.gemo.apex.core.exception.SessionOwnershipException;
import org.gemo.apex.core.exception.SessionStateException;

/**
 * 将请求、定义和持久化快照组装为可运行的 {@link ApexAgent}。
 *
 * <p>新请求建立新的 Turn；恢复请求按当前模板重新装配 Agent，并复用运行时快照中的挂起 ToolCall。
 */
public final class ApexAgentFactory {
    private static final System.Logger LOG = System.getLogger(ApexAgentFactory.class.getName());
    private final AgentDefinitionAssembler assembler = new AgentDefinitionAssembler();

    /** 创建新 Turn，并先后追加用户消息和保存 IN_PROGRESS 会话快照。 */
    public ApexAgent createNew(AgentRequest request, AgentPorts ports) {
        ports.cancellationToken().throwIfCancellationRequested();
        Optional<SessionSnapshot> existing = ports.sessionRepository().load(request.sessionId());
        existing.ifPresent(snapshot -> validateNewOwner(request, snapshot));
        // 先把当前定义解析为快照，后续 Turn 可在配置变更后仍按该快照恢复。
        AgentAssemblyResult assembly =
                assembler.assemble(request.sessionId(), request.agentKey(), existing, ports);
        long turnNo = existing.map(snapshot -> snapshot.currentTurnNo() + 1).orElse(1L);
        var now = ports.timeProvider().now();
        TurnSnapshot turn = new TurnSnapshot(turnNo, TurnStatus.IN_PROGRESS, null, now, null);
        Set<String> activated =
                new LinkedHashSet<>(
                        existing.map(SessionSnapshot::activatedSkills).orElse(Set.of()));
        Set<String> removedSkills = new LinkedHashSet<>(activated);
        activated.retainAll(assembly.definition().definition().enabledSkills());
        removedSkills.removeAll(activated);
        if (!removedSkills.isEmpty()) {
            LOG.log(
                    System.Logger.Level.WARNING,
                    "Agent 定义已移除激活 Skill，当前 Turn 清理: " + removedSkills);
        }
        long nextSort = existing.map(SessionSnapshot::nextMessageSortNo).orElse(0L);
        SessionSnapshot snapshot =
                new SessionSnapshot(
                        SnapshotSchemaVersion.V1,
                        request.sessionId(),
                        request.userId(),
                        request.agentKey(),
                        SessionStatus.IN_PROGRESS,
                        turnNo,
                        assembly.effectiveEnabledTools(),
                        activated,
                        assembly.historicalToolBindings(),
                        ApexAgentContext.recovery(assembly.definition()),
                        turn,
                        null,
                        nextSort + 1,
                        now);
        AgentMessageEntry user =
                new AgentMessageEntry(
                        ports.idGenerator().newEntryId(),
                        request.sessionId(),
                        turnNo,
                        nextSort,
                        MessageRole.USER,
                        MessageType.TEXT,
                        request.query(),
                        Map.of(),
                        now);
        ConversationWindow window = loadWindow(request.sessionId(), ports);
        ApexAgentContext context =
                new ApexAgentContext(
                        ports,
                        assembly.definition(),
                        assembly.toolCatalog(),
                        snapshot,
                        window,
                        null);
        ports.cancellationToken().throwIfCancellationRequested();
        context.appendConversation(List.of(user));
        ports.cancellationToken().throwIfCancellationRequested();
        context.save();
        return new ApexAgent(context);
    }

    /** 创建人工介入后的恢复执行，并校验所有权、挂起状态和 ToolCall 的唯一对应关系。 */
    public ApexAgent createResumed(HumanResponseCommand command, AgentPorts ports) {
        SessionSnapshot snapshot =
                ports.sessionRepository()
                        .load(command.sessionId())
                        .orElseThrow(() -> new SessionStateException("恢复会话不存在"));
        if (!snapshot.userId().equals(command.userId())
                || !snapshot.agentKey().equals(command.agentKey())) {
            throw new SessionOwnershipException("恢复请求不属于当前用户或 Agent");
        }
        if (snapshot.status() != SessionStatus.HUMAN_IN_THE_LOOP) {
            throw new SessionStateException("会话不处于人工介入状态");
        }
        SuspendedToolBatch suspended =
                Objects.requireNonNull(snapshot.suspendedToolBatch(), "suspendedToolBatch");
        if (suspended.toolCalls().size()
                != snapshot.activeTurn().currentIteration().modelResponse().toolCalls().size()) {
            throw new SessionStateException("挂起 ToolCall 批次无法完整定位");
        }
        AgentAssemblyResult assembly =
                assembler.assemble(
                        command.sessionId(), command.agentKey(), Optional.of(snapshot), ports);
        Set<String> activated = new LinkedHashSet<>(snapshot.activatedSkills());
        activated.retainAll(assembly.definition().definition().enabledSkills());
        SessionSnapshot resumedSnapshot =
                new SessionSnapshot(
                        snapshot.schemaVersion(),
                        snapshot.sessionId(),
                        snapshot.userId(),
                        snapshot.agentKey(),
                        snapshot.status(),
                        snapshot.currentTurnNo(),
                        assembly.effectiveEnabledTools(),
                        activated,
                        assembly.historicalToolBindings(),
                        ApexAgentContext.recovery(assembly.definition()),
                        snapshot.activeTurn(),
                        suspended,
                        snapshot.nextMessageSortNo(),
                        snapshot.lastActiveTime());
        return new ApexAgent(
                new ApexAgentContext(
                        ports,
                        assembly.definition(),
                        assembly.toolCatalog(),
                        resumedSnapshot,
                        loadWindow(command.sessionId(), ports),
                        command.response()));
    }

    private ConversationWindow loadWindow(String sessionId, AgentPorts ports) {
        ports.cancellationToken().throwIfCancellationRequested();
        ConversationWindow window =
                ports.windowManager()
                        .prepare(new ConversationWindowRequest(new ConversationQuery(sessionId)));
        ports.cancellationToken().throwIfCancellationRequested();
        return window;
    }

    /** 拒绝越权请求及尚未结束或等待人工响应的会话上创建新 Turn。 */
    private void validateNewOwner(AgentRequest request, SessionSnapshot snapshot) {
        if (!snapshot.userId().equals(request.userId())
                || !snapshot.agentKey().equals(request.agentKey())) {
            throw new SessionOwnershipException("会话不属于当前用户或 Agent");
        }
        if (snapshot.status() == SessionStatus.HUMAN_IN_THE_LOOP) {
            throw new SessionStateException("存在待处理人工介入，不能创建新 Turn");
        }
        if (snapshot.status() == SessionStatus.IN_PROGRESS) {
            throw new SessionStateException("会话仍在执行中");
        }
    }
}
