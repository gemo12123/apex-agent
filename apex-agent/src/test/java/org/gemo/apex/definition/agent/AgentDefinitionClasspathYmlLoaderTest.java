package org.gemo.apex.definition.agent;

import org.gemo.apex.config.ApexGlobalProperties;
import org.gemo.apex.config.model.AgentConfig;
import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.config.model.HookBindingConfig;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.prompt.StageSystemPrompt;
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
    void loadShouldFallBackToGlobalDefinitionWhenWorkspaceConfigMissing() {
        AgentConfig global = new AgentConfig();
        global.setAgentKey("agent-global");
        global.setDefaultExecutionMode(ModeEnum.PLAN_EXECUTOR);
        global.setMcps(List.of("global-mcp"));
        global.setSubAgents(List.of("global-sub"));
        global.setSkills(List.of("global-skill"));
        global.setHooks(AgentHooksConfig.builder()
                .preToolCall(List.of(HookBindingConfig.builder().bean("globalHook").build()))
                .build());
        apexGlobalProperties.setAgents(Map.of("agent-global", global));

        stubCommonWorkspaceFilesMissing("classpath:agents/agent-global/");
        when(resourceLoader.getResource("classpath:agents/defaults/REACT_PROMPT.md"))
                .thenReturn(resource("default react"));
        when(resourceLoader.getResource("classpath:agents/defaults/PLAN_EXECUTOR_WRITE_PLAN_PROMPT.md"))
                .thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/defaults/PLAN_EXECUTOR_RUN_PROMPT.md"))
                .thenReturn(missing());

        AgentDefinition definition = loader.load("agent-global");

        assertEquals(ModeEnum.PLAN_EXECUTOR, definition.defaultExecutionMode());
        assertEquals(List.of("global-mcp"), definition.mcpNames());
        assertEquals(List.of("global-sub"), definition.subAgentNames());
        assertEquals(List.of("global-skill"), definition.skillNames());
        assertEquals("globalHook", definition.hooks().getPreToolCall().getFirst().getBean());
        assertEquals("default react", definition.reactPrompt());
        assertEquals("", definition.agentRules());
    }

    @Test
    void loadShouldOverrideSubAgentsSkillsHooksAndExecutionModeFromWorkspaceConfig() {
        AgentConfig global = new AgentConfig();
        global.setAgentKey("agent-override");
        global.setDefaultExecutionMode(ModeEnum.REACT);
        global.setSubAgents(List.of("global-sub"));
        global.setSkills(List.of("global-skill"));
        global.setHooks(AgentHooksConfig.builder()
                .preToolCall(List.of(HookBindingConfig.builder().bean("globalHook").build()))
                .build());
        apexGlobalProperties.setAgents(Map.of("agent-override", global));

        when(resourceLoader.getResource("classpath:agents/agent-override/config.yml"))
                .thenReturn(resource("""
                        allow-sub-agents: [workspace-sub]
                        allow-skills: [workspace-skill]
                        default-execution-mode: PLAN_EXECUTOR
                        hooks:
                          pre-tool-call:
                            - bean: workspaceHook
                              tools: [meeting_tool]
                        """));
        stubCommonPromptAndRulesMissing("classpath:agents/agent-override/");
        stubCommonDefaultsMissing();

        AgentDefinition definition = loader.load("agent-override");

        assertEquals(ModeEnum.PLAN_EXECUTOR, definition.defaultExecutionMode());
        assertEquals(List.of("workspace-sub"), definition.subAgentNames());
        assertEquals(List.of("workspace-skill"), definition.skillNames());
        assertEquals("workspaceHook", definition.hooks().getPreToolCall().getFirst().getBean());
        assertEquals(List.of("meeting_tool"), definition.hooks().getPreToolCall().getFirst().getTools());
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
    void loadShouldUseGlobalHooksWhenWorkspaceDoesNotDeclareHooks() {
        AgentConfig global = new AgentConfig();
        global.setAgentKey("agent-hooks-fallback");
        global.setDefaultExecutionMode(ModeEnum.REACT);
        global.setHooks(AgentHooksConfig.builder()
                .postToolCall(List.of(HookBindingConfig.builder().bean("postHook").build()))
                .build());
        apexGlobalProperties.setAgents(Map.of("agent-hooks-fallback", global));

        when(resourceLoader.getResource("classpath:agents/agent-hooks-fallback/config.yml"))
                .thenReturn(resource("allow-mcps: [workspace-mcp]\n"));
        stubCommonPromptAndRulesMissing("classpath:agents/agent-hooks-fallback/");
        stubCommonDefaultsMissing();

        AgentDefinition definition = loader.load("agent-hooks-fallback");

        assertEquals("postHook", definition.hooks().getPostToolCall().getFirst().getBean());
        assertEquals(List.of("workspace-mcp"), definition.mcpNames());
    }

    @Test
    void loadShouldParseAllLifecycleHookPoints() {
        AgentConfig global = new AgentConfig();
        global.setAgentKey("agent-lifecycle-hooks");
        global.setDefaultExecutionMode(ModeEnum.REACT);
        apexGlobalProperties.setAgents(Map.of("agent-lifecycle-hooks", global));
        when(resourceLoader.getResource("classpath:agents/agent-lifecycle-hooks/config.yml"))
                .thenReturn(resource("""
                        hooks:
                          turn-start: [{bean: turnStart}]
                          trace-start: [{bean: traceStart}]
                          pre-model-call: [{bean: preModel}]
                          post-model-call: [{bean: postModel}]
                          pre-tool-call: [{bean: preTool}]
                          post-tool-call: [{bean: postTool}]
                          trace-end: [{bean: traceEnd}]
                          turn-end: [{bean: turnEnd}]
                        """));
        stubCommonPromptAndRulesMissing("classpath:agents/agent-lifecycle-hooks/");
        stubCommonDefaultsMissing();

        AgentHooksConfig hooks = loader.load("agent-lifecycle-hooks").hooks();

        assertEquals("turnStart", hooks.getTurnStart().getFirst().getBean());
        assertEquals("traceStart", hooks.getTraceStart().getFirst().getBean());
        assertEquals("preModel", hooks.getPreModelCall().getFirst().getBean());
        assertEquals("postModel", hooks.getPostModelCall().getFirst().getBean());
        assertEquals("preTool", hooks.getPreToolCall().getFirst().getBean());
        assertEquals("postTool", hooks.getPostToolCall().getFirst().getBean());
        assertEquals("traceEnd", hooks.getTraceEnd().getFirst().getBean());
        assertEquals("turnEnd", hooks.getTurnEnd().getFirst().getBean());
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
    void loadShouldFailWhenWorkspaceExecutionModeIsInvalid() {
        AgentConfig global = new AgentConfig();
        global.setAgentKey("agent-invalid-mode");
        global.setDefaultExecutionMode(ModeEnum.REACT);
        apexGlobalProperties.setAgents(Map.of("agent-invalid-mode", global));

        when(resourceLoader.getResource("classpath:agents/agent-invalid-mode/config.yml"))
                .thenReturn(resource("default-execution-mode: invalid-mode\n"));

        assertThrows(IllegalStateException.class, () -> loader.load("agent-invalid-mode"));
    }

    @Test
    void loadShouldFailWhenAgentIsMissing() {
        assertThrows(IllegalStateException.class, () -> loader.load("missing-agent"));
    }

    @Test
    void loadShouldUsePromptFallbackChainFromDefaultsThenStagePrompt() {
        AgentConfig global = new AgentConfig();
        global.setAgentKey("agent-prompt");
        global.setDefaultExecutionMode(ModeEnum.REACT);
        apexGlobalProperties.setAgents(Map.of("agent-prompt", global));

        stubCommonWorkspaceFilesMissing("classpath:agents/agent-prompt/");
        when(resourceLoader.getResource("classpath:agents/defaults/REACT_PROMPT.md"))
                .thenReturn(resource("default react prompt"));
        when(resourceLoader.getResource("classpath:agents/defaults/PLAN_EXECUTOR_WRITE_PLAN_PROMPT.md"))
                .thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/defaults/PLAN_EXECUTOR_RUN_PROMPT.md"))
                .thenReturn(missing());

        AgentDefinition definition = loader.load("agent-prompt");

        assertEquals("default react prompt", definition.reactPrompt());
        assertEquals(StageSystemPrompt.getPlanExecutorWritePlanPrompt(), definition.planExecutorWritePlanPrompt());
        assertEquals(StageSystemPrompt.getPlanExecutorRunPrompt(), definition.planExecutorRunPrompt());
    }

    @Test
    void loadShouldReturnEmptyRulesWhenAgentRulesFileMissing() {
        AgentConfig global = new AgentConfig();
        global.setAgentKey("agent-rules");
        global.setDefaultExecutionMode(ModeEnum.REACT);
        apexGlobalProperties.setAgents(Map.of("agent-rules", global));

        stubCommonWorkspaceFilesMissing("classpath:agents/agent-rules/");
        stubCommonDefaultsMissing();

        AgentDefinition definition = loader.load("agent-rules");

        assertEquals("", definition.agentRules());
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

    private void stubCommonWorkspaceFilesMissing(String workspaceRoot) {
        when(resourceLoader.getResource(workspaceRoot + "config.yml")).thenReturn(missing());
        stubCommonPromptAndRulesMissing(workspaceRoot);
    }

    private void stubCommonPromptAndRulesMissing(String workspaceRoot) {
        when(resourceLoader.getResource(workspaceRoot + "REACT_PROMPT.md")).thenReturn(missing());
        when(resourceLoader.getResource(workspaceRoot + "PLAN_EXECUTOR_WRITE_PLAN_PROMPT.md")).thenReturn(missing());
        when(resourceLoader.getResource(workspaceRoot + "PLAN_EXECUTOR_RUN_PROMPT.md")).thenReturn(missing());
        when(resourceLoader.getResource(workspaceRoot + "AGENT.md")).thenReturn(missing());
    }

    private void stubCommonDefaultsMissing() {
        when(resourceLoader.getResource("classpath:agents/defaults/REACT_PROMPT.md")).thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/defaults/PLAN_EXECUTOR_WRITE_PLAN_PROMPT.md"))
                .thenReturn(missing());
        when(resourceLoader.getResource("classpath:agents/defaults/PLAN_EXECUTOR_RUN_PROMPT.md"))
                .thenReturn(missing());
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
