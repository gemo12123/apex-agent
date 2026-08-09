package org.gemo.apex.core.agent;

import java.util.Objects;
import org.gemo.apex.common.tool.CancellationToken;
import org.gemo.apex.extension.conversation.ConversationCompactionPolicy;
import org.gemo.apex.extension.conversation.ConversationCompactor;
import org.gemo.apex.extension.conversation.ConversationWindowManager;
import org.gemo.apex.extension.definition.AgentDefinitionProvider;
import org.gemo.apex.extension.event.AgentEventPublisher;
import org.gemo.apex.extension.hook.HookResolver;
import org.gemo.apex.extension.id.IdGenerator;
import org.gemo.apex.extension.model.ModelGateway;
import org.gemo.apex.extension.repository.ConversationRepository;
import org.gemo.apex.extension.repository.SessionRepository;
import org.gemo.apex.extension.skill.SkillProvider;
import org.gemo.apex.extension.time.TimeProvider;
import org.gemo.apex.extension.tool.ToolAvailabilityProvider;
import org.gemo.apex.extension.tool.ToolProvider;

/** 单次请求所需的全部外部端口；该对象不进入快照。 */
public record AgentPorts(
        AgentDefinitionProvider definitionProvider,
        ToolProvider toolProvider,
        ToolAvailabilityProvider toolAvailabilityProvider,
        HookResolver hookResolver,
        ModelGateway modelGateway,
        SessionRepository sessionRepository,
        ConversationRepository conversationRepository,
        ConversationWindowManager windowManager,
        ConversationCompactionPolicy compactionPolicy,
        ConversationCompactor compactor,
        SkillProvider skillProvider,
        AgentEventPublisher eventPublisher,
        CancellationToken cancellationToken,
        IdGenerator idGenerator,
        TimeProvider timeProvider,
        String finalIterationInstruction) {
    public AgentPorts {
        Objects.requireNonNull(definitionProvider, "definitionProvider");
        Objects.requireNonNull(toolProvider, "toolProvider");
        Objects.requireNonNull(toolAvailabilityProvider, "toolAvailabilityProvider");
        Objects.requireNonNull(hookResolver, "hookResolver");
        Objects.requireNonNull(modelGateway, "modelGateway");
        Objects.requireNonNull(sessionRepository, "sessionRepository");
        Objects.requireNonNull(conversationRepository, "conversationRepository");
        Objects.requireNonNull(windowManager, "windowManager");
        Objects.requireNonNull(compactionPolicy, "compactionPolicy");
        Objects.requireNonNull(compactor, "compactor");
        Objects.requireNonNull(skillProvider, "skillProvider");
        Objects.requireNonNull(eventPublisher, "eventPublisher");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(idGenerator, "idGenerator");
        Objects.requireNonNull(timeProvider, "timeProvider");
        if (finalIterationInstruction == null || finalIterationInstruction.isBlank()) {
            throw new IllegalArgumentException("finalIterationInstruction 不能为空");
        }
    }
}
