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
import org.gemo.apex.kit.hook.SkillActivationStateHook;
import org.gemo.apex.kit.hook.TodoMiddleware;
import org.gemo.apex.kit.tool.ActivateSkillTool;
import org.gemo.apex.kit.tool.ReadSkillResourceTool;
import org.gemo.apex.kit.tool.WriteTodosTool;
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
import org.springframework.core.Ordered;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ApexAgentPlatformConfigurationTest {
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
        return new ApexAgentPlatformConfiguration()
                .apexAgentRuntime(
                        properties,
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
                        beans.getBeanProvider(SkillProvider.class));
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
            this.order = order;
            skill = new SkillDefinition(new SkillMeta("pdf", "PDF"), instructions);
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
