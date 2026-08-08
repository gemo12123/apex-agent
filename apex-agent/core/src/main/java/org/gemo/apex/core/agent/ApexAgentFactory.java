package org.gemo.apex.core.agent;

import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.execution.TurnStatus;
import org.gemo.apex.common.intervention.HumanResponseCommand;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.snapshot.*;
import org.gemo.apex.common.agent.ToolSetDefinition;
import org.gemo.apex.core.definition.AgentDefinitionAssembler;
import org.gemo.apex.core.definition.AgentDefinitionValidator;
import org.gemo.apex.core.exception.InvalidAgentDefinitionException;
import org.gemo.apex.core.exception.SessionOwnershipException;
import org.gemo.apex.core.exception.SessionStateException;
import org.gemo.apex.core.intervention.HumanResponseParser;
import org.gemo.apex.core.tool.ToolCatalog;

import java.util.*;

/**
 * 将请求、定义和持久化快照组装为可运行的 {@link ApexAgent}。
 *
 * <p>新请求建立新的 Turn；恢复请求严格复用快照中的定义和挂起 ToolCall，避免运行时配置变化
 * 改写历史执行语义。</p>
 */
public final class ApexAgentFactory {
    private static final System.Logger LOG = System.getLogger(ApexAgentFactory.class.getName());
    private final AgentDefinitionAssembler assembler = new AgentDefinitionAssembler();
    private final AgentDefinitionValidator validator = new AgentDefinitionValidator();
    private final HumanResponseParser responseParser = new HumanResponseParser();

    /**
     * 创建新 Turn，并先后追加用户消息和保存 IN_PROGRESS 会话快照。
     */
    public ApexAgent createNew(AgentRequest request, AgentPorts ports) {
        ports.cancellationToken().throwIfCancellationRequested();
        Optional<SessionSnapshot> existing = ports.sessionRepository().load(request.sessionId());
        existing.ifPresent(snapshot -> validateNewOwner(request, snapshot));
        // 先把当前定义解析为快照，后续 Turn 可在配置变更后仍按该快照恢复。
        AgentAssemblyResult assembly = assembler.assemble(request.sessionId(), request.agentKey(), existing, ports);
        long turnNo = existing.map(snapshot -> snapshot.currentTurnNo() + 1).orElse(1L);
        var now = ports.timeProvider().now();
        TurnSnapshot turn = new TurnSnapshot(turnNo, TurnStatus.IN_PROGRESS, null, now, null);
        Set<String> activated = new LinkedHashSet<>(existing.map(SessionSnapshot::activatedSkills).orElse(Set.of()));
        Set<String> removedSkills = new LinkedHashSet<>(activated);
        activated.retainAll(assembly.definition().definition().enabledSkills());
        removedSkills.removeAll(activated);
        if (!removedSkills.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "Agent 定义已移除激活 Skill，当前 Turn 清理: " + removedSkills);
        }
        long nextSort = existing.map(SessionSnapshot::nextMessageSortNo).orElse(0L);
        SessionSnapshot snapshot = new SessionSnapshot(SnapshotSchemaVersion.V1, request.sessionId(),
                request.userId(), request.agentKey(), SessionStatus.IN_PROGRESS, turnNo,
                assembly.effectiveEnabledTools(), activated, assembly.historicalToolBindings(),
                ApexAgentContext.recovery(assembly.definition()), turn, null, nextSort + 1, now);
        AgentMessageEntry user = new AgentMessageEntry(ports.idGenerator().newEntryId(), request.sessionId(),
                turnNo, nextSort, MessageRole.USER, MessageType.TEXT, request.query(), Map.of(), now);
        ports.cancellationToken().throwIfCancellationRequested();
        ports.conversationRepository().append(List.of(user));
        ports.cancellationToken().throwIfCancellationRequested();
        ports.sessionRepository().save(snapshot);
        return new ApexAgent(new ApexAgentContext(ports, assembly.definition(), assembly.toolCatalog(), snapshot,
                null));
    }

    /**
     * 创建人工介入后的恢复执行，并校验所有权、挂起状态和 ToolCall 的唯一对应关系。
     */
    public ApexAgent createResumed(HumanResponseCommand command, AgentPorts ports) {
        SessionSnapshot snapshot = ports.sessionRepository().load(command.sessionId())
                .orElseThrow(() -> new SessionStateException("恢复会话不存在"));
        if (!snapshot.userId().equals(command.userId()) || !snapshot.agentKey().equals(command.agentKey())) {
            throw new SessionOwnershipException("恢复请求不属于当前用户或 Agent");
        }
        if (snapshot.status() != SessionStatus.HUMAN_IN_THE_LOOP) {
            throw new SessionStateException("会话不处于人工介入状态");
        }
        SuspendedToolCall suspended = Objects.requireNonNull(snapshot.suspendedToolCall(), "suspendedToolCall");
        long matches = snapshot.activeTurn().currentIteration().modelResponse().toolCalls().stream()
                .filter(call -> call.toolCallId().equals(suspended.toolCallId())).count();
        if (matches != 1) throw new SessionStateException("挂起 ToolCall 无法唯一定位");
        var submission = responseParser.parse(command, suspended);
        AgentDefinitionRecoverySnapshot recovery = snapshot.activeDefinition();
        AgentDefinition definition = new AgentDefinition(recovery.schemaVersion(), recovery.metadata(),
                recovery.prompt(), recovery.messageCompression(),
                new ToolSetDefinition(recovery.availableTools(), snapshot.enabledTools()),
                recovery.enabledSkills(), recovery.subAgents(), recovery.hooks());
        ToolCatalog catalog = new ToolCatalog(ports.toolProvider().loadTools(recovery));
        Set<String> resolved = catalog.ordered().stream().map(tool -> tool.definition().name())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> missing = new LinkedHashSet<>(recovery.availableTools());
        missing.removeAll(resolved);
        var availability = ports.toolAvailabilityProvider().current();
        boolean onlyKnownUnavailable = missing.stream().allMatch(name -> availability.unavailableToolNames().contains(name)
                || availability.unavailableSources().stream().anyMatch(source -> name.startsWith(source.stableNamePrefix())));
        if (!onlyKnownUnavailable) {
            throw new InvalidAgentDefinitionException("恢复快照中的 Hook/Tool 无法解析");
        }
        validator.validateRecoveryBindings(definition, ports);
        return new ApexAgent(new ApexAgentContext(ports, new AgentDefinitionSnapshot(definition), catalog, snapshot,
                submission));
    }

    /** 拒绝越权请求及尚未结束或等待人工响应的会话上创建新 Turn。 */
    private void validateNewOwner(AgentRequest request, SessionSnapshot snapshot) {
        if (!snapshot.userId().equals(request.userId()) || !snapshot.agentKey().equals(request.agentKey())) {
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
