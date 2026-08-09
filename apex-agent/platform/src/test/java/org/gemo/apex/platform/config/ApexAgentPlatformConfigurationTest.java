package org.gemo.apex.platform.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.extension.definition.AgentDefinitionProvider;
import org.gemo.apex.extension.model.ModelGateway;
import org.gemo.apex.platform.web.sse.RequestBoundAgentEventPublisherFactory;
import org.gemo.apex.runtime.api.*;
import org.gemo.apex.runtime.repository.memory.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.*;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

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

    private static ApexAgentRuntime createRuntime(DefaultListableBeanFactory beans) {
        return new ApexAgentPlatformConfiguration()
                .apexAgentRuntime(
                        definitions(),
                        new InMemorySessionRepository(),
                        new InMemoryConversationRepository(),
                        new RequestBoundAgentEventPublisherFactory(),
                        beans.getBeanProvider(ModelGateway.class),
                        beans.getBeanProvider(ChatModel.class),
                        beans.getBeanProvider(ToolCallingManager.class),
                        beans.getBeanProvider(org.gemo.apex.extension.tool.AgentTool.class),
                        beans.getBeanProvider(ToolCallback.class),
                        beans.getBeanProvider(ToolCallbackProvider.class),
                        beans.getBeanProvider(PlatformHookRegistration.class),
                        beans.getBeanProvider(org.gemo.apex.common.skill.SkillDefinition.class));
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
