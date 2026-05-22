package org.gemo.apex.definition.agent;

import org.gemo.apex.config.ApexGlobalProperties;
import org.gemo.apex.config.model.AgentConfig;
import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.config.model.HookBindingConfig;
import org.gemo.apex.constant.ModeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDefinitionClasspathYmlLoaderTest {

    private ResourceLoader resourceLoader;
    private ApexGlobalProperties apexGlobalProperties;
    private AgentDefinitionClasspathYmlLoader loader;

    @BeforeEach
    void setUp() {
        resourceLoader = mock(ResourceLoader.class);
        apexGlobalProperties = new ApexGlobalProperties();
        loader = new AgentDefinitionClasspathYmlLoader(apexGlobalProperties, resourceLoader);
    }

    @Test
    void loadShouldUseResolvedWorkspaceRootForConfigPromptsAndRules() {
        AgentConfig global = new AgentConfig();
        global.setAgentKey("agent-1");
        global.setDefaultExecutionMode(ModeEnum.REACT);
        global.setWorkspace("classpath:custom/agent-1/");
        global.setMcps(List.of("global-mcp"));
        apexGlobalProperties.setAgents(Map.of("agent-1", global));

        when(resourceLoader.getResource("classpath:custom/agent-1/config.yml"))
                .thenReturn(resource("allow-mcps: [workspace-mcp]\ndefault-execution-mode: REACT\n"));
        when(resourceLoader.getResource("classpath:custom/agent-1/REACT_PROMPT.md"))
                .thenReturn(resource("workspace react"));
        when(resourceLoader.getResource("classpath:custom/agent-1/PLAN_EXECUTOR_WRITE_PLAN_PROMPT.md"))
                .thenReturn(missing());
        when(resourceLoader.getResource("classpath:custom/agent-1/PLAN_EXECUTOR_RUN_PROMPT.md"))
                .thenReturn(missing());
        when(resourceLoader.getResource("classpath:custom/agent-1/AGENT.md"))
                .thenReturn(resource("NO_DELETE"));
        when(resourceLoader.getResource("classpath:agents/defaults/PLAN_EXECUTOR_WRITE_PLAN_PROMPT.md"))
                .thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/defaults/PLAN_EXECUTOR_RUN_PROMPT.md"))
                .thenReturn(missing());

        AgentDefinition definition = loader.load("agent-1");

        assertEquals(List.of("workspace-mcp"), definition.mcpNames());
        assertEquals("workspace react", definition.reactPrompt());
        assertEquals("NO_DELETE", definition.agentRules());
    }

    @Test
    void loadShouldDisableHooksWhenWorkspaceConfigDeclaresEmptyHooksArray() {
        AgentConfig global = new AgentConfig();
        global.setAgentKey("agent-2");
        global.setDefaultExecutionMode(ModeEnum.REACT);
        global.setHooks(AgentHooksConfig.builder()
                .preToolCall(List.of(HookBindingConfig.builder().bean("globalHook").build()))
                .build());
        apexGlobalProperties.setAgents(Map.of("agent-2", global));

        when(resourceLoader.getResource("classpath:agents/agent-2/config.yml"))
                .thenReturn(resource("hooks: []\ndefault-execution-mode: REACT\n"));
        when(resourceLoader.getResource("classpath:agents/agent-2/REACT_PROMPT.md")).thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/defaults/REACT_PROMPT.md")).thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/agent-2/PLAN_EXECUTOR_WRITE_PLAN_PROMPT.md"))
                .thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/defaults/PLAN_EXECUTOR_WRITE_PLAN_PROMPT.md"))
                .thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/agent-2/PLAN_EXECUTOR_RUN_PROMPT.md"))
                .thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/defaults/PLAN_EXECUTOR_RUN_PROMPT.md"))
                .thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/agent-2/AGENT.md")).thenReturn(missing());

        AgentDefinition definition = loader.load("agent-2");

        assertTrue(definition.hooks().isDisabled());
        assertTrue(definition.hooks().getPreToolCall().isEmpty());
    }

    @Test
    void loadShouldFailWhenWorkspaceConfigIsInvalid() {
        AgentConfig global = new AgentConfig();
        global.setAgentKey("agent-3");
        global.setDefaultExecutionMode(ModeEnum.REACT);
        apexGlobalProperties.setAgents(Map.of("agent-3", global));

        when(resourceLoader.getResource("classpath:agents/agent-3/config.yml"))
                .thenReturn(resource("default-execution-mode: [broken"));

        assertThrows(IllegalStateException.class, () -> loader.load("agent-3"));
    }

    @Test
    void loadShouldUseSameCachedDefinitionInstance() {
        AgentConfig global = new AgentConfig();
        global.setAgentKey("agent-4");
        global.setDefaultExecutionMode(ModeEnum.REACT);
        apexGlobalProperties.setAgents(Map.of("agent-4", global));

        when(resourceLoader.getResource("classpath:agents/agent-4/config.yml")).thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/agent-4/REACT_PROMPT.md")).thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/defaults/REACT_PROMPT.md")).thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/agent-4/PLAN_EXECUTOR_WRITE_PLAN_PROMPT.md"))
                .thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/defaults/PLAN_EXECUTOR_WRITE_PLAN_PROMPT.md"))
                .thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/agent-4/PLAN_EXECUTOR_RUN_PROMPT.md"))
                .thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/defaults/PLAN_EXECUTOR_RUN_PROMPT.md"))
                .thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/agent-4/AGENT.md")).thenReturn(missing());

        AgentDefinition first = loader.load("agent-4");
        AgentDefinition second = loader.load("agent-4");

        assertSame(first, second);
    }

    private Resource resource(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    private Resource missing() {
        return new ByteArrayResource(new byte[0]) {
            @Override
            public boolean exists() {
                return false;
            }
        };
    }
}
