package org.gemo.apex.platform.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.skill.*;
import org.gemo.apex.core.agent.AgentRunOutcome;
import org.gemo.apex.extension.definition.AgentDefinitionProvider;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.extension.model.ModelGateway;
import org.gemo.apex.extension.skill.SkillProvider;
import org.gemo.apex.kit.hook.AvailableSkillsPromptHook;
import org.gemo.apex.kit.hook.SkillActivationStateHook;
import org.gemo.apex.kit.hook.TodoMiddleware;
import org.gemo.apex.kit.hook.ToolResultTruncateHook;
import org.gemo.apex.kit.tool.ActivateSkillTool;
import org.gemo.apex.kit.tool.InspectToolResultTool;
import org.gemo.apex.kit.tool.ReadSkillResourceTool;
import org.gemo.apex.kit.tool.WriteTodosTool;
import org.gemo.apex.platform.skill.RuntimeSkillRegistry;
import org.gemo.apex.platform.web.sse.RequestBoundAgentEventPublisherFactory;
import org.gemo.apex.platform.web.sse.SseEmitterAgentEventPublisher;
import org.gemo.apex.runtime.api.*;
import org.gemo.apex.runtime.repository.memory.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.*;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ApexAgentPlatformConfigurationTest {
    @Test
    void registersToolResultTruncateHookWithNewStableName() {
        ToolResultTruncateHook hook =
                new ApexAgentPlatformConfiguration().toolResultTruncateHook();

        assertEquals(ToolResultTruncateHook.REGISTRATION_NAME, hook.name());
        assertEquals("toolResultTruncateHook", hook.name());
    }

    @Test
    void registersToolResultInspectorWithStableName() {
        InspectToolResultTool tool =
                new ApexAgentPlatformConfiguration().inspectToolResultTool();

        assertEquals(InspectToolResultTool.NAME, tool.definition().name());
        assertEquals("inspect_tool_result", tool.definition().name());
    }

    @Test
    void leavesToolResultInspectorDisabledByAgentDefaults() {
        ApexAgentPlatformProperties.Tools defaults =
                new ApexAgentPlatformProperties.Tools();

        assertFalse(defaults.getAvailable().contains(InspectToolResultTool.NAME));
        assertFalse(defaults.getDefaultEnabled().contains(InspectToolResultTool.NAME));
    }

    @Test
    void resolvesToolResultInspectorWhenExplicitlyConfigured() {
        DefaultListableBeanFactory beans = beans();
        beans.registerSingleton(
                "inspectToolResultTool",
                new ApexAgentPlatformConfiguration().inspectToolResultTool());
        RequestBoundAgentEventPublisherFactory publishers =
                new RequestBoundAgentEventPublisherFactory();

        try (var runtime = createRuntime(beans, inspectorDefinitions(), publishers)) {
            var publisher = new SseEmitterAgentEventPublisher(new SseEmitter());
            assertInstanceOf(
                    AgentRunOutcome.Completed.class,
                    publishers
                            .prepare(
                                    "inspect",
                                    "default",
                                    "u",
                                    publisher,
                                    () ->
                                            runtime.newAgent(
                                                    new AgentRequest(
                                                            "inspect", "default", "u", "q")))
                            .run());
        }
    }

    @Test
    void exposesFinalSkillRegistryToSpringConsumersWithoutSelfAggregation() {
        AtomicInteger metadataLoads = new AtomicInteger();
        SkillProvider source =
                new OrderedSkillProvider(
                        10, "聚合结果", "Skill 指令", metadataLoads, new AtomicInteger());

        try (var context = new AnnotationConfigApplicationContext()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context,
                    "apex.platform.agents.test.name=测试 Agent",
                    "apex.platform.agents.test.description=测试",
                    "apex.platform.agents.test.prompt.system="
                            + "classpath:agents/default_agent/REACT_PROMPT.md");
            context.register(ApexAgentPlatformConfiguration.class);
            context.registerBean(
                    ModelGateway.class,
                    () -> (request, observer) -> new ModelResponse("完成", List.of(), Map.of()));
            context.registerBean(InMemorySessionRepository.class);
            context.registerBean(InMemoryConversationRepository.class);
            context.registerBean("sourceSkillProvider", SkillProvider.class, () -> source);
            context.registerBean(SkillProviderConsumer.class);
            context.refresh();

            RuntimeSkillRegistry registry = context.getBean(RuntimeSkillRegistry.class);
            assertSame(registry, context.getBean(SkillProvider.class));
            assertSame(registry, context.getBean(SkillProviderConsumer.class).skillProvider());
            assertEquals(List.of(new SkillMeta("pdf", "聚合结果")), registry.loadSkills());
            assertEquals(1, metadataLoads.get());
            assertEquals(1, context.getBeansOfType(AvailableSkillsPromptHook.class).size());
            assertNotNull(context.getBean(ApexAgentRuntime.class));
        }
    }

    @Test
    void collectsDirectCallbacksAndSnapshotsProvidersOnce() {
        AtomicInteger providerReads = new AtomicInteger();
        DefaultListableBeanFactory beans = beans();
        beans.registerSingleton("toolCallingManager", ToolCallingManager.builder().build());
        beans.registerSingleton("directCallback", callback("direct"));
        beans.registerSingleton(
                "callbackProvider",
                (ToolCallbackProvider)
                        () -> {
                            providerReads.incrementAndGet();
                            return new ToolCallback[] {callback("provided")};
                        });

        try (var runtime = createRuntime(beans)) {
            assertNotNull(runtime);
            assertEquals(1, providerReads.get());
        }
    }

    @Test
    void rejectsCallbacksWhenNoUniqueManagerExists() {
        DefaultListableBeanFactory beans = beans();
        beans.registerSingleton("directCallback", callback("direct"));

        RuntimeConfigurationException error =
                assertThrows(RuntimeConfigurationException.class, () -> createRuntime(beans));

        assertTrue(error.getMessage().contains("唯一 ToolCallingManager"));
    }

    @Test
    void collectsExplicitKitSkillToolAndHookBeans() {
        DefaultListableBeanFactory beans = beans();
        SkillDefinition skill = new SkillDefinition(new SkillMeta("pdf", "PDF"), "使用 PDF 指令");
        SkillProvider skillProvider = provider(skill);
        beans.registerSingleton("pdfSkillProvider", skillProvider);
        beans.registerSingleton("activateSkillTool", new ActivateSkillTool(skillProvider));
        beans.registerSingleton("skillActivationStateHook", new SkillActivationStateHook());

        RequestBoundAgentEventPublisherFactory publishers =
                new RequestBoundAgentEventPublisherFactory();
        ApexAgentPlatformProperties properties = new ApexAgentPlatformProperties();
        properties.getSkills().setPath(" ");
        try (var runtime = createRuntime(beans, skillDefinitions(), publishers, properties)) {
            var publisher = new SseEmitterAgentEventPublisher(new SseEmitter());
            assertInstanceOf(
                    AgentRunOutcome.Completed.class,
                    publishers
                            .prepare(
                                    "skill",
                                    "default",
                                    "u",
                                    publisher,
                                    () ->
                                            runtime.newAgent(
                                                    new AgentRequest("skill", "default", "u", "q")))
                            .run());
        }
    }

    @Test
    void usesConfiguredSkillPathInsteadOfDefaultClasspathLocation() throws Exception {
        Path root = Files.createTempDirectory(Path.of("target"), "platform-skills");
        Path skill = Files.createDirectory(root.resolve("pdf"));
        Files.writeString(
                skill.resolve("SKILL.md"), "---\nname: pdf\ndescription: PDF\n---\n使用 PDF 指令");
        ApexAgentPlatformProperties properties = new ApexAgentPlatformProperties();
        assertEquals("classpath:skills", properties.getSkills().getPath());
        properties.getSkills().setPath(root.toString());

        RequestBoundAgentEventPublisherFactory publishers =
                new RequestBoundAgentEventPublisherFactory();
        try (var runtime = createRuntime(beans(), skillOnlyDefinitions(), publishers, properties)) {
            var publisher = new SseEmitterAgentEventPublisher(new SseEmitter());
            assertInstanceOf(
                    AgentRunOutcome.Completed.class,
                    publishers
                            .prepare(
                                    "configured-skill",
                                    "default",
                                    "u",
                                    publisher,
                                    () ->
                                            runtime.newAgent(
                                                    new AgentRequest(
                                                            "configured-skill",
                                                            "default",
                                                            "u",
                                                            "q")))
                            .run());
        }
    }

    @Test
    void aggregatesSkillProvidersInSpringOrderAndRoutesToLastDuplicate() {
        AtomicInteger firstMetadataLoads = new AtomicInteger();
        AtomicInteger firstResourceLoads = new AtomicInteger();
        AtomicInteger secondMetadataLoads = new AtomicInteger();
        AtomicInteger secondResourceLoads = new AtomicInteger();
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        Deque<ModelResponse> responses =
                new ArrayDeque<>(
                        List.of(
                                new ModelResponse(
                                        "",
                                        List.of(
                                                new org.gemo.apex.common.tool.ToolCall(
                                                        "read-1",
                                                        ReadSkillResourceTool.NAME,
                                                        0,
                                                        Map.of(
                                                                "skillName",
                                                                "pdf",
                                                                "path",
                                                                "references/guide.txt"),
                                                        Map.of())),
                                        Map.of()),
                                new ModelResponse("完成", List.of(), Map.of())));
        beans.registerSingleton(
                "modelGateway",
                (ModelGateway) (request, observer) -> responses.removeFirst());
        beans.registerSingleton(
                "firstSkillProvider",
                new OrderedSkillProvider(
                        10, "第一版", firstMetadataLoads, firstResourceLoads));
        beans.registerSingleton(
                "secondSkillProvider",
                new OrderedSkillProvider(
                        20, "第二版", secondMetadataLoads, secondResourceLoads));

        RequestBoundAgentEventPublisherFactory publishers =
                new RequestBoundAgentEventPublisherFactory();
        try (var runtime = createRuntime(beans, resourceSkillDefinitions(), publishers)) {
            var publisher = new SseEmitterAgentEventPublisher(new SseEmitter());
            assertInstanceOf(
                    AgentRunOutcome.Completed.class,
                    publishers
                            .prepare(
                                    "ordered-skills",
                                    "default",
                                    "u",
                                    publisher,
                                    () ->
                                            runtime.newAgent(
                                                    new AgentRequest(
                                                            "ordered-skills",
                                                            "default",
                                                            "u",
                                                            "q")))
                            .run());
        }

        assertEquals(1, firstMetadataLoads.get());
        assertEquals(1, secondMetadataLoads.get());
        assertEquals(0, firstResourceLoads.get());
        assertEquals(1, secondResourceLoads.get());
    }

    @Test
    void registersAvailableSkillsPromptHookAgainstTheFinalSkillRegistry() {
        AtomicReference<org.gemo.apex.common.model.ModelRequest> seen = new AtomicReference<>();
        AtomicInteger firstMetadataLoads = new AtomicInteger();
        AtomicInteger secondMetadataLoads = new AtomicInteger();
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton(
                "modelGateway",
                (ModelGateway)
                        (request, observer) -> {
                            seen.set(request);
                            return new ModelResponse("完成", List.of(), Map.of());
                        });
        beans.registerSingleton(
                "firstSkillProvider",
                new OrderedSkillProvider(
                        10, "第一版", "第一版指令", firstMetadataLoads, new AtomicInteger()));
        beans.registerSingleton(
                "secondSkillProvider",
                new OrderedSkillProvider(
                        20, "最终 & <PDF>", "第二版指令", secondMetadataLoads, new AtomicInteger()));

        RequestBoundAgentEventPublisherFactory publishers =
                new RequestBoundAgentEventPublisherFactory();
        try (var runtime = createRuntime(beans, availableSkillsDefinitions(), publishers)) {
            var publisher = new SseEmitterAgentEventPublisher(new SseEmitter());
            assertInstanceOf(
                    AgentRunOutcome.Completed.class,
                    publishers
                            .prepare(
                                    "available-skills",
                                    "default",
                                    "u",
                                    publisher,
                                    () ->
                                            runtime.newAgent(
                                                    new AgentRequest(
                                                            "available-skills",
                                                            "default",
                                                            "u",
                                                            "q")))
                            .run());
        }

        assertNotNull(seen.get());
        assertEquals(
                """
                系统
                <available_skills>
                <skill>
                <name>pdf</name>
                <description>最终 &amp; &lt;PDF&gt;</description>
                </skill>
                </available_skills>\
                """,
                seen.get().systemPrompt());
        assertEquals(1, firstMetadataLoads.get());
        assertEquals(1, secondMetadataLoads.get());
    }

    @Test
    void registersTodoComponentsWithoutExposingThemToUnconfiguredAgent() {
        AtomicReference<org.gemo.apex.common.model.ModelRequest> seen = new AtomicReference<>();
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton(
                "modelGateway",
                (ModelGateway)
                        (request, observer) -> {
                            seen.set(request);
                            return new ModelResponse("完成", List.of(), Map.of());
                        });
        ApexAgentPlatformConfiguration configuration = new ApexAgentPlatformConfiguration();
        WriteTodosTool tool = configuration.writeTodosTool();
        TodoMiddleware middleware = configuration.todoMiddleware();
        beans.registerSingleton("writeTodosTool", tool);
        beans.registerSingleton("todoMiddleware", middleware);

        RequestBoundAgentEventPublisherFactory publishers =
                new RequestBoundAgentEventPublisherFactory();
        try (var runtime = createRuntime(beans, definitions(), publishers)) {
            var publisher = new SseEmitterAgentEventPublisher(new SseEmitter());
            assertInstanceOf(
                    AgentRunOutcome.Completed.class,
                    publishers
                            .prepare(
                                    "todo-unconfigured",
                                    "default",
                                    "u",
                                    publisher,
                                    () ->
                                            runtime.newAgent(
                                                    new AgentRequest(
                                                            "todo-unconfigured",
                                                            "default",
                                                            "u",
                                                            "q")))
                            .run());
        }

        assertEquals(WriteTodosTool.NAME, tool.definition().name());
        assertEquals(TodoMiddleware.REGISTRATION_NAME, middleware.name());
        assertNotNull(seen.get());
        assertTrue(seen.get().tools().isEmpty());
        assertFalse(seen.get().systemPrompt().contains("todo_list_system"));
    }

    private static ApexAgentRuntime createRuntime(DefaultListableBeanFactory beans) {
        return createRuntime(beans, definitions());
    }

    private static ApexAgentRuntime createRuntime(
            DefaultListableBeanFactory beans, AgentDefinitionProvider definitions) {
        return createRuntime(beans, definitions, new RequestBoundAgentEventPublisherFactory());
    }

    private static ApexAgentRuntime createRuntime(
            DefaultListableBeanFactory beans,
            AgentDefinitionProvider definitions,
            RequestBoundAgentEventPublisherFactory publishers) {
        return createRuntime(beans, definitions, publishers, new ApexAgentPlatformProperties());
    }

    private static ApexAgentRuntime createRuntime(
            DefaultListableBeanFactory beans,
            AgentDefinitionProvider definitions,
            RequestBoundAgentEventPublisherFactory publishers,
            ApexAgentPlatformProperties properties) {
        ApexAgentPlatformConfiguration configuration = new ApexAgentPlatformConfiguration();
        RuntimeSkillRegistry skillRegistry =
                configuration.runtimeSkillRegistry(
                        properties, beans.getBeanProvider(SkillProvider.class), beans);
        beans.registerSingleton(
                "availableSkillsPromptHook",
                configuration.availableSkillsPromptHook(skillRegistry));
        return configuration.apexAgentRuntime(
                definitions,
                new InMemorySessionRepository(),
                new InMemoryConversationRepository(),
                publishers,
                beans.getBeanProvider(ModelGateway.class),
                beans.getBeanProvider(ChatModel.class),
                beans.getBeanProvider(ToolCallingManager.class),
                beans.getBeanProvider(org.gemo.apex.extension.tool.AgentTool.class),
                beans.getBeanProvider(ToolCallback.class),
                beans.getBeanProvider(ToolCallbackProvider.class),
                hookBeans(beans),
                skillRegistry);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ObjectProvider<LifecycleHook<?, ?>> hookBeans(DefaultListableBeanFactory beans) {
        return (ObjectProvider) beans.getBeanProvider(LifecycleHook.class);
    }

    private static DefaultListableBeanFactory beans() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton(
                "modelGateway",
                (ModelGateway) (request, observer) -> new ModelResponse("完成", List.of(), Map.of()));
        return beans;
    }

    private static AgentDefinitionProvider definitions() {
        AgentDefinition definition =
                new AgentDefinition(
                        "1.0.0",
                        new AgentMetadata("default", "默认", "测试"),
                        new PromptDefinition("系统", 2),
                        new MessageCompressionDefinition(false, 10),
                        new ToolSetDefinition(Set.of(), Set.of()),
                        Set.of(),
                        Map.of(),
                        Map.of());
        return new AgentDefinitionProvider() {
            @Override
            public AgentDefinition load(String agentKey) {
                return definition;
            }

            @Override
            public List<AgentMetadata> listAgents() {
                return List.of(definition.metadata());
            }
        };
    }

    private static AgentDefinitionProvider skillDefinitions() {
        AgentDefinition definition =
                new AgentDefinition(
                        "1.0.0",
                        new AgentMetadata("default", "默认", "测试"),
                        new PromptDefinition("系统", 2),
                        new MessageCompressionDefinition(false, 10),
                        new ToolSetDefinition(
                                Set.of(ActivateSkillTool.NAME), Set.of(ActivateSkillTool.NAME)),
                        Set.of("pdf"),
                        Map.of(),
                        Map.of(
                                HookPoint.POST_TOOL_CALL,
                                List.of(
                                        new HookBinding(
                                                "skill-state",
                                                SkillActivationStateHook.REGISTRATION_NAME,
                                                0,
                                                true,
                                                List.of(ActivateSkillTool.NAME),
                                                Map.of()))));
        return new AgentDefinitionProvider() {
            @Override
            public AgentDefinition load(String agentKey) {
                return definition;
            }

            @Override
            public List<AgentMetadata> listAgents() {
                return List.of(definition.metadata());
            }
        };
    }

    private static AgentDefinitionProvider inspectorDefinitions() {
        AgentDefinition definition =
                new AgentDefinition(
                        "1.0.0",
                        new AgentMetadata("default", "默认", "测试"),
                        new PromptDefinition("系统", 2),
                        new MessageCompressionDefinition(false, 10),
                        new ToolSetDefinition(
                                Set.of(InspectToolResultTool.NAME),
                                Set.of(InspectToolResultTool.NAME)),
                        Set.of(),
                        Map.of(),
                        Map.of());
        return new AgentDefinitionProvider() {
            @Override
            public AgentDefinition load(String agentKey) {
                return definition;
            }

            @Override
            public List<AgentMetadata> listAgents() {
                return List.of(definition.metadata());
            }
        };
    }

    private static AgentDefinitionProvider skillOnlyDefinitions() {
        AgentDefinition definition =
                new AgentDefinition(
                        "1.0.0",
                        new AgentMetadata("default", "默认", "测试"),
                        new PromptDefinition("系统", 2),
                        new MessageCompressionDefinition(false, 10),
                        new ToolSetDefinition(Set.of(), Set.of()),
                        Set.of("pdf"),
                        Map.of(),
                        Map.of());
        return new AgentDefinitionProvider() {
            @Override
            public AgentDefinition load(String agentKey) {
                return definition;
            }

            @Override
            public List<AgentMetadata> listAgents() {
                return List.of(definition.metadata());
            }
        };
    }

    private static AgentDefinitionProvider resourceSkillDefinitions() {
        AgentDefinition definition =
                new AgentDefinition(
                        "1.0.0",
                        new AgentMetadata("default", "默认", "测试"),
                        new PromptDefinition("系统", 2),
                        new MessageCompressionDefinition(false, 10),
                        new ToolSetDefinition(
                                Set.of(ReadSkillResourceTool.NAME),
                                Set.of(ReadSkillResourceTool.NAME)),
                        Set.of("pdf"),
                        Map.of(),
                        Map.of());
        return new AgentDefinitionProvider() {
            @Override
            public AgentDefinition load(String agentKey) {
                return definition;
            }

            @Override
            public List<AgentMetadata> listAgents() {
                return List.of(definition.metadata());
            }
        };
    }

    private static AgentDefinitionProvider availableSkillsDefinitions() {
        AgentDefinition definition =
                new AgentDefinition(
                        "1.0.0",
                        new AgentMetadata("default", "默认", "测试"),
                        new PromptDefinition("系统\n{skills}", 2),
                        new MessageCompressionDefinition(false, 10),
                        new ToolSetDefinition(Set.of(), Set.of()),
                        Set.of("pdf"),
                        Map.of(),
                        Map.of(
                                HookPoint.AGENT_BUILD,
                                List.of(
                                        new HookBinding(
                                                "available-skills",
                                                AvailableSkillsPromptHook.REGISTRATION_NAME,
                                                0,
                                                true,
                                                List.of(),
                                                Map.of()))));
        return new AgentDefinitionProvider() {
            @Override
            public AgentDefinition load(String agentKey) {
                return definition;
            }

            @Override
            public List<AgentMetadata> listAgents() {
                return List.of(definition.metadata());
            }
        };
    }

    private static SkillProvider provider(SkillDefinition skill) {
        return new SkillProvider() {
            @Override
            public List<SkillMeta> loadSkills() {
                return List.of(skill.meta());
            }

            @Override
            public SkillDefinition loadSkill(String skillName) {
                return skill;
            }

            @Override
            public String loadResource(String skillName, String resourcePath) {
                return resourcePath;
            }

            @Override
            public String loadResource(String path) {
                return path;
            }
        };
    }

    private record SkillProviderConsumer(SkillProvider skillProvider) {}

    private static final class OrderedSkillProvider implements SkillProvider, Ordered {
        private final int order;
        private final SkillDefinition skill;
        private final AtomicInteger metadataLoads;
        private final AtomicInteger resourceLoads;

        private OrderedSkillProvider(
                int order,
                String instructions,
                AtomicInteger metadataLoads,
                AtomicInteger resourceLoads) {
            this(order, "PDF", instructions, metadataLoads, resourceLoads);
        }

        private OrderedSkillProvider(
                int order,
                String description,
                String instructions,
                AtomicInteger metadataLoads,
                AtomicInteger resourceLoads) {
            this.order = order;
            skill = new SkillDefinition(new SkillMeta("pdf", description), instructions);
            this.metadataLoads = metadataLoads;
            this.resourceLoads = resourceLoads;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public List<SkillMeta> loadSkills() {
            metadataLoads.incrementAndGet();
            return List.of(skill.meta());
        }

        @Override
        public SkillDefinition loadSkill(String skillName) {
            return skill;
        }

        @Override
        public String loadResource(String skillName, String resourcePath) {
            resourceLoads.incrementAndGet();
            return skill.instructions();
        }

        @Override
        public String loadResource(String path) {
            return loadResource("pdf", path);
        }
    }

    private static ToolCallback callback(String name) {
        return new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return new DefaultToolDefinition(name, "测试工具", "{}");
            }

            @Override
            public String call(String input) {
                return "结果";
            }
        };
    }
}
