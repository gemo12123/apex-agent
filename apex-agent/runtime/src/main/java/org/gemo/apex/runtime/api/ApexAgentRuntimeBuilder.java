package org.gemo.apex.runtime.api;

import java.time.*;
import java.util.*;
import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.skill.SkillMeta;
import org.gemo.apex.common.tool.*;
import org.gemo.apex.core.agent.AgentPorts;
import org.gemo.apex.extension.definition.AgentDefinitionProvider;
import org.gemo.apex.extension.event.*;
import org.gemo.apex.extension.hook.*;
import org.gemo.apex.extension.id.IdGenerator;
import org.gemo.apex.extension.model.ModelGateway;
import org.gemo.apex.extension.repository.*;
import org.gemo.apex.extension.skill.SkillProvider;
import org.gemo.apex.extension.tool.*;
import org.gemo.apex.runtime.conversation.*;
import org.gemo.apex.runtime.definition.*;
import org.gemo.apex.runtime.event.*;
import org.gemo.apex.runtime.execution.*;
import org.gemo.apex.runtime.model.springai.SpringAiModelGateway;
import org.gemo.apex.runtime.model.springai.SpringAiToolCallbackAgentTool;
import org.gemo.apex.runtime.registry.*;
import org.gemo.apex.runtime.repository.memory.*;
import org.gemo.apex.runtime.resource.*;
import org.gemo.apex.runtime.skill.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

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
    private final List<ToolCallback> toolCallbacks = new ArrayList<>();
    private final List<ToolCallbackProvider> toolCallbackProviders = new ArrayList<>();
    private ToolCallingManager toolCallingManager;
    private final Map<HookRegistry.Key, LifecycleHook<?, ?>> hooks = new LinkedHashMap<>();
    private SkillProvider skillProvider;
    private final List<AutoCloseable> owned = new ArrayList<>();
    private int max = 30;

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

    public ApexAgentRuntimeBuilder toolCallingManager(ToolCallingManager v) {
        toolCallingManager = v;
        return this;
    }

    public ApexAgentRuntimeBuilder registerToolCallback(ToolCallback v) {
        toolCallbacks.add(v);
        return this;
    }

    public ApexAgentRuntimeBuilder registerToolCallbackProvider(ToolCallbackProvider v) {
        toolCallbackProviders.add(v);
        return this;
    }

    public ApexAgentRuntimeBuilder registerHook(LifecycleHook<?, ?> v) {
        Objects.requireNonNull(v, "hook");
        String name = v.name();
        if (name == null || name.isBlank()) {
            throw new RuntimeConfigurationException("Hook 注册名称不能为空");
        }
        var k = new HookRegistry.Key(v.descriptor().hookPoint(), name);
        if (hooks.putIfAbsent(k, v) != null) {
            throw new RuntimeConfigurationException("Hook 重名: " + name);
        }
        return this;
    }

    public ApexAgentRuntimeBuilder skillProvider(SkillProvider v) {
        skillProvider = Objects.requireNonNull(v, "skillProvider");
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

    public ApexAgentRuntime build() {
        List<ToolCallback> callbackSnapshot = new ArrayList<>(toolCallbacks);
        for (ToolCallbackProvider callbackProvider : toolCallbackProviders) {
            ToolCallback[] provided =
                    Objects.requireNonNull(
                            callbackProvider.getToolCallbacks(), "ToolCallbackProvider 返回值不能为空");
            callbackSnapshot.addAll(List.of(provided));
        }
        List<String> e = new ArrayList<>();
        if ((model == null) == (chat == null)) {
            e.add("modelGateway/chatModel 必须且只能配置一个");
        }
        if (provider != null && definition != null) {
            e.add("Definition/Provider 只能配置一个");
        }
        if (max < 1) {
            e.add("maxIterations 非法");
        }
        if (!callbackSnapshot.isEmpty() && toolCallingManager == null) {
            e.add("注册 ToolCallback 时必须配置唯一 ToolCallingManager");
        }
        callbackSnapshot.stream()
                .filter(callback -> callback.getToolMetadata().returnDirect())
                .map(callback -> callback.getToolDefinition().name())
                .forEach(name -> e.add("暂不支持 returnDirect=true 的 ToolCallback: " + name));
        if (!e.isEmpty()) {
            throw new RuntimeConfigurationException(String.join("; ", e));
        }
        var gateway = model != null ? model : new SpringAiModelGateway(chat);
        var defs =
                provider != null
                        ? provider
                        : new ProgrammaticAgentDefinitionProvider(
                                definition != null ? definition : defaults());
        var sr = sessions != null ? sessions : new InMemorySessionRepository();
        var cr = conversations != null ? conversations : new InMemoryConversationRepository();
        var ts = new ArrayList<>(tools);
        callbackSnapshot.stream()
                .map(callback -> new SpringAiToolCallbackAgentTool(callback, toolCallingManager))
                .forEach(ts::add);
        SkillProvider skills = skillProvider != null ? skillProvider : EmptySkillProvider.INSTANCE;
        var tr = new ToolRegistry(ts, skills);
        var hr = new HookRegistry(hooks);
        var pf = publishers != null ? publishers : new PrintAgentEventPublisherFactory();
        var co = coordinator != null ? coordinator : new InMemorySessionExecutionCoordinator();
        var active = new ActiveExecutionRegistry();
        var ids =
                new IdGenerator() {
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
        ApexAgentRuntime.Ports ports =
                (p, c) ->
                        new AgentPorts(
                                defs,
                                tr,
                                () -> new ToolAvailabilitySnapshot(Set.of(), List.of()),
                                hr,
                                gateway,
                                sr,
                                cr,
                                DefaultConversationServices.window(cr),
                                DefaultConversationServices.policy(),
                                DefaultConversationServices.compactor(gateway, c.token()),
                                skills,
                                p,
                                c.token(),
                                ids,
                                Instant::now,
                                "请直接给出最终答案，不再调用工具。");
        return new ApexAgentRuntime(ports, co, pf, active, new RuntimeResources(owned));
    }

    private enum EmptySkillProvider implements SkillProvider {
        INSTANCE;

        @Override
        public List<SkillMeta> loadSkills() {
            return List.of();
        }

        @Override
        public SkillDefinition loadSkill(String skillName) {
            throw missing(skillName);
        }

        @Override
        public String loadResource(String skillName, String resourcePath) {
            throw missing(skillName);
        }

        @Override
        public String loadResource(String path) {
            if (path == null) {
                throw new IllegalArgumentException("Skill 资源路径不能为空");
            }
            int separator = path.indexOf('/');
            String skillName = separator < 0 ? path : path.substring(0, separator);
            throw missing(skillName);
        }

        private IllegalArgumentException missing(String skillName) {
            return new IllegalArgumentException("Skill 不存在: " + skillName);
        }
    }

    private AgentDefinition defaults() {
        return new AgentDefinition(
                DefinitionSchemaVersion.V1,
                new AgentMetadata("default", "默认 Agent", "runtime 默认 ReAct Agent"),
                new PromptDefinition("你是一个可靠的智能助手。", max),
                new MessageCompressionDefinition(true, 100),
                new ToolSetDefinition(Set.of(), Set.of()),
                Set.of(),
                Map.of(),
                Map.of());
    }
}
