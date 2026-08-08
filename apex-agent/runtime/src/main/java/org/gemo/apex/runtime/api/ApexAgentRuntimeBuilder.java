package org.gemo.apex.runtime.api;

import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.tool.*;
import org.gemo.apex.core.agent.AgentPorts;
import org.gemo.apex.extension.definition.AgentDefinitionProvider;
import org.gemo.apex.extension.event.*;
import org.gemo.apex.extension.hook.*;
import org.gemo.apex.extension.model.ModelGateway;
import org.gemo.apex.extension.repository.*;
import org.gemo.apex.extension.tool.*;
import org.gemo.apex.runtime.conversation.*;
import org.gemo.apex.runtime.definition.*;
import org.gemo.apex.runtime.event.*;
import org.gemo.apex.runtime.execution.*;
import org.gemo.apex.runtime.model.springai.SpringAiModelGateway;
import org.gemo.apex.runtime.registry.*;
import org.gemo.apex.runtime.repository.memory.*;
import org.gemo.apex.runtime.resource.*;
import org.gemo.apex.runtime.skill.*;
import org.springframework.ai.chat.model.ChatModel;

import java.time.*;
import java.util.*;

public final class ApexAgentRuntimeBuilder {
    private ModelGateway model;
    private ChatModel chat;
    private AgentDefinitionProvider provider;
    private AgentDefinition definition;
    private SessionRepository sessions;
    private ConversationRepository conversations;
    private AgentEventPublisherFactory publishers;
    private SessionExecutionCoordinator coordinator;
    private final List<AgentTool> tools = new ArrayList<>();
    private final Map<HookRegistry.Key, LifecycleHook<?, ?>> hooks = new LinkedHashMap<>();
    private final List<SkillDefinition> skills = new ArrayList<>();
    private final List<AutoCloseable> owned = new ArrayList<>();
    private int max = 30;
    private long hard = 1_000_000;

    public ApexAgentRuntimeBuilder modelGateway(ModelGateway v) {
        model = v;
        return this;
    }

    public ApexAgentRuntimeBuilder chatModel(ChatModel v) {
        chat = v;
        return this;
    }

    public ApexAgentRuntimeBuilder agentDefinition(AgentDefinition v) {
        definition = v;
        return this;
    }

    public ApexAgentRuntimeBuilder agentDefinitionProvider(AgentDefinitionProvider v) {
        provider = v;
        return this;
    }

    public ApexAgentRuntimeBuilder sessionRepository(SessionRepository v) {
        sessions = v;
        return this;
    }

    public ApexAgentRuntimeBuilder conversationRepository(ConversationRepository v) {
        conversations = v;
        return this;
    }

    public ApexAgentRuntimeBuilder defaultEventPublisherFactory(AgentEventPublisherFactory v) {
        publishers = v;
        return this;
    }

    public ApexAgentRuntimeBuilder sessionExecutionCoordinator(SessionExecutionCoordinator v) {
        coordinator = v;
        return this;
    }

    public ApexAgentRuntimeBuilder registerTool(AgentTool v) {
        tools.add(v);
        return this;
    }

    public ApexAgentRuntimeBuilder registerHook(String n, LifecycleHook<?, ?> v) {
        var k = new HookRegistry.Key(v.descriptor().hookPoint(), n);
        if (hooks.putIfAbsent(k, v) != null) throw new RuntimeConfigurationException("Hook 重名: " + n);
        return this;
    }

    public ApexAgentRuntimeBuilder registerSkill(SkillDefinition v) {
        skills.add(v);
        return this;
    }

    public ApexAgentRuntimeBuilder ownedResource(AutoCloseable v) {
        owned.add(v);
        return this;
    }

    public ApexAgentRuntimeBuilder borrowedResource(AutoCloseable v) {
        return this;
    }

    public ApexAgentRuntimeBuilder maxIterations(int v) {
        max = v;
        return this;
    }

    public ApexAgentRuntimeBuilder modelRequestHardLimit(long v) {
        hard = v;
        return this;
    }

    public ApexAgentRuntime build() {
        List<String> e = new ArrayList<>();
        if ((model == null) == (chat == null)) e.add("modelGateway/chatModel 必须且只能配置一个");
        if (provider != null && definition != null) e.add("Definition/Provider 只能配置一个");
        if (max < 1) e.add("maxIterations 非法");
        if (!e.isEmpty()) throw new RuntimeConfigurationException(String.join("; ", e));
        var gateway = model != null ? model : new SpringAiModelGateway(chat);
        var defs = provider != null ? provider : new ProgrammaticAgentDefinitionProvider(definition != null ? definition : defaults());
        var sr = sessions != null ? sessions : new InMemorySessionRepository();
        var cr = conversations != null ? conversations : new InMemoryConversationRepository();
        var ts = new ArrayList<>(tools);
        ts.add(activationTool());
        var skillsRegistry = new RuntimeSkillRegistry(skills);
        var tr = new ToolRegistry(ts, skillsRegistry);
        var hr = new HookRegistry(hooks);
        var pf = publishers != null ? publishers : new PrintAgentEventPublisherFactory();
        var co = coordinator != null ? coordinator : new InMemorySessionExecutionCoordinator();
        var active = new ActiveExecutionRegistry();
        var ids = new org.gemo.apex.extension.id.IdGenerator() {
            String n() {
                return UUID.randomUUID().toString();
            }

            public String newExecutionId() {
                return n();
            }

            public String newEntryId() {
                return n();
            }

            public String newInvocationId() {
                return n();
            }

            public String newConfirmationId() {
                return n();
            }

            public String newSubSessionId() {
                return n();
            }

            public String newCompactionId() {
                return n();
            }
        };
        int mi = max;
        long hl = hard;
        ApexAgentRuntime.Ports ports = (p, c) -> new AgentPorts(defs, tr, () -> new ToolAvailabilitySnapshot(Set.of(), List.of()), hr, gateway, sr, cr, DefaultConversationServices.window(cr), DefaultConversationServices.policy(), DefaultConversationServices.compactor(), skillsRegistry, skillsRegistry, p, c.token(), ids, Instant::now, mi, hl, "请直接给出最终答案，不再调用工具。");
        return new ApexAgentRuntime(ports, co, pf, active, new RuntimeResources(owned));
    }

    private AgentTool activationTool() {
        return new AgentTool() {
            private final ToolDefinition d = new ToolDefinition("activate_skill", "激活一个 Skill", "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"}},\"required\":[\"command\"]}", Map.of());

            public ToolDefinition definition() {
                return d;
            }

            public ToolResult execute(ToolCall c, ToolExecutionContext x, ToolExecutionObserver o) {
                throw new IllegalStateException("由 core SkillActivationCoordinator 执行");
            }
        };
    }

    private AgentDefinition defaults() {
        return new AgentDefinition(DefinitionSchemaVersion.V1, new AgentMetadata("default", "默认 Agent", "runtime 默认 ReAct Agent"), new PromptDefinition("你是一个可靠的智能助手。", max), new MessageCompressionDefinition(true, 100), new ToolSetDefinition(Set.of(), Set.of()), Set.of(), Map.of(), Map.of());
    }
}
