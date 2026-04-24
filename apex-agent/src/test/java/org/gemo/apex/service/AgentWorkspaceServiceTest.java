package org.gemo.apex.service;

import org.gemo.apex.config.model.AgentConfig;
import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.config.model.HookBindingConfig;
import org.gemo.apex.config.provider.AgentConfigProvider;
import org.gemo.apex.config.provider.McpConfigProvider;
import org.gemo.apex.config.provider.SkillConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class AgentWorkspaceServiceTest {

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private AgentConfigProvider agentConfigProvider;

    @Mock
    private McpConfigProvider mcpConfigProvider;

    @Mock
    private SkillConfigProvider skillConfigProvider;

    private AgentWorkspaceService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AgentWorkspaceService();
        ReflectionTestUtils.setField(service, "resourceLoader", resourceLoader);
        ReflectionTestUtils.setField(service, "agentConfigProvider", agentConfigProvider);
        ReflectionTestUtils.setField(service, "mcpConfigProvider", mcpConfigProvider);
        ReflectionTestUtils.setField(service, "skillConfigProvider", skillConfigProvider);
    }

    @Test
    void getHooksShouldFallBackToGlobalHooksWhenWorkspaceDoesNotDeclareHooks() {
        AgentConfig global = new AgentConfig();
        global.setAgentKey("default_agent");
        global.setHooks(AgentHooksConfig.builder()
                .preToolCall(List.of(HookBindingConfig.builder()
                        .bean("toolConfirmHook")
                        .enabled(true)
                        .tools(List.of("meeting_tool"))
                        .order(100)
                        .build()))
                .build());

        when(agentConfigProvider.getAgentConfig("default_agent")).thenReturn(global);
        when(resourceLoader.getResource("classpath:agents/default_agent/config.yml"))
                .thenReturn(resource("default-execution-mode: REACT\n"));

        AgentHooksConfig resolved = service.getHooks("default_agent");

        assertFalse(resolved.isDisabled());
        assertEquals(List.of("meeting_tool"), resolved.getPreToolCall().getFirst().getTools());
    }

    @Test
    void getHooksShouldReplaceGlobalHooksWhenWorkspaceDeclaresHooksBlock() {
        AgentConfig global = new AgentConfig();
        global.setAgentKey("default_agent");
        global.setHooks(AgentHooksConfig.builder()
                .preToolCall(List.of(HookBindingConfig.builder().bean("globalHook").build()))
                .build());

        when(agentConfigProvider.getAgentConfig("default_agent")).thenReturn(global);
        when(resourceLoader.getResource("classpath:agents/default_agent/config.yml"))
                .thenReturn(resource("""
                        hooks:
                          pre-tool-call:
                            - bean: workspaceHook
                              enabled: true
                              tools: ["contacts_tool"]
                        """));

        AgentHooksConfig resolved = service.getHooks("default_agent");

        assertEquals("workspaceHook", resolved.getPreToolCall().getFirst().getBean());
        assertEquals(List.of("contacts_tool"), resolved.getPreToolCall().getFirst().getTools());
    }

    @Test
    void getHooksShouldTreatHooksEmptyArrayAsDisableAll() {
        AgentConfig global = new AgentConfig();
        global.setAgentKey("default_agent");
        global.setHooks(AgentHooksConfig.builder()
                .preToolCall(List.of(HookBindingConfig.builder().bean("toolConfirmHook").build()))
                .build());

        when(agentConfigProvider.getAgentConfig("default_agent")).thenReturn(global);
        when(resourceLoader.getResource("classpath:agents/default_agent/config.yml"))
                .thenReturn(resource("hooks: []\n"));

        AgentHooksConfig resolved = service.getHooks("default_agent");

        assertTrue(resolved.isDisabled());
        assertTrue(resolved.getPreToolCall().isEmpty());
        assertTrue(resolved.getPostToolCall().isEmpty());
    }

    private Resource resource(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "config.yml";
            }
        };
    }
}
