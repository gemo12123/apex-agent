package org.gemo.apex.platform.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gemo.apex.common.hook.HookPoint;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class SpringPropertiesAgentDefinitionProviderTest {
    /** 完整配置应转换并由轻量列表直接返回 */
    @Test
    void convertsCompleteConfigurationAndReturnsLightweightListDirectly() {
        var properties = new ApexAgentPlatformProperties();
        var agent = new ApexAgentPlatformProperties.Agent();
        agent.setName("默认");
        agent.setDescription("描述");
        agent.getPrompt().setSystem("classpath:agents/default_agent/REACT_PROMPT.md");
        agent.getMessageCompression().setTokenThreshold(32000L);
        agent.getMessageCompression().setCharacterHardLimit(120000L);
        agent.getTools().setAvailable(Set.of("search"));
        agent.getTools().setDefaultEnabled(Set.of("search"));
        var hook = new ApexAgentPlatformProperties.Hook();
        hook.setId("confirm-search");
        hook.setHook("toolConfirmHook");
        hook.setTools(List.of("search"));
        agent.setHooks(Map.of("PRE_TOOL_CALL", List.of(hook)));
        var agents = new LinkedHashMap<String, ApexAgentPlatformProperties.Agent>();
        agents.put("default", agent);
        properties.setAgents(agents);

        var provider =
                new SpringPropertiesAgentDefinitionProvider(
                        properties, new DefaultResourceLoader());

        assertEquals("default", provider.listAgents().getFirst().agentKey());
        assertEquals(Set.of("search"), provider.load("default").tools().defaultEnabledTools());
        assertTrue(provider.load("default").prompt().systemPrompt().contains("通用智能体"));
        assertEquals(32000L, provider.load("default").messageCompression().tokenThreshold());
        assertEquals(120000L, provider.load("default").messageCompression().characterHardLimit());
        assertEquals(
                "toolConfirmHook",
                provider.load("default").hooks().get(HookPoint.PRE_TOOL_CALL).getFirst().hook());
    }

    /** 多定义源和缺失Prompt应在构造期失败 */
    @Test
    void rejectsMultipleDefinitionSourcesAndMissingPromptAtConstruction() {
        var properties = new ApexAgentPlatformProperties();
        properties.setDefinitionResource("classpath:agents.yml");
        properties.getAgents().put("default", new ApexAgentPlatformProperties.Agent());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SpringPropertiesAgentDefinitionProvider(
                                properties, new DefaultResourceLoader()));

        properties.setDefinitionResource(null);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SpringPropertiesAgentDefinitionProvider(
                                properties, new DefaultResourceLoader()));
    }
}
