package org.gemo.apex.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.LinkedHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SpringPropertiesAgentDefinitionProviderTest {
    @Test
    void 完整配置应转换并由轻量列表直接返回() {
        var properties = new ApexAgentPlatformProperties();
        var agent = new ApexAgentPlatformProperties.Agent();
        agent.setName("默认");
        agent.setDescription("描述");
        agent.getPrompt().setSystem("classpath:agents/default_agent/REACT_PROMPT.md");
        agent.getTools().setAvailable(Set.of("search"));
        agent.getTools().setDefaultEnabled(Set.of("search"));
        var agents = new LinkedHashMap<String, ApexAgentPlatformProperties.Agent>();
        agents.put("default", agent);
        properties.setAgents(agents);

        var provider = new SpringPropertiesAgentDefinitionProvider(properties, new DefaultResourceLoader());

        assertEquals("default", provider.listAgents().getFirst().agentKey());
        assertEquals(Set.of("search"), provider.load("default").tools().defaultEnabledTools());
        assertTrue(provider.load("default").prompt().systemPrompt().contains("通用智能体"));
    }

    @Test
    void 多定义源和缺失Prompt应在构造期失败() {
        var properties = new ApexAgentPlatformProperties();
        properties.setDefinitionResource("classpath:agents.yml");
        properties.getAgents().put("default", new ApexAgentPlatformProperties.Agent());
        assertThrows(IllegalArgumentException.class, () ->
                new SpringPropertiesAgentDefinitionProvider(properties, new DefaultResourceLoader()));

        properties.setDefinitionResource(null);
        assertThrows(IllegalArgumentException.class, () ->
                new SpringPropertiesAgentDefinitionProvider(properties, new DefaultResourceLoader()));
    }
}
