package org.gemo.apex.config;

import lombok.Data;
import org.gemo.apex.config.model.AgentConfig;
import org.gemo.apex.config.model.McpServerConfig;
import org.gemo.apex.config.model.SkillConfig;
import org.gemo.apex.config.provider.AgentConfigProvider;
import org.gemo.apex.config.provider.McpConfigProvider;
import org.gemo.apex.config.provider.SkillConfigProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Apex Global Configuration Properties.
 * Acts as the default provider for Agents, MCPs, and Skills via
 * application.yml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.global")
public class ApexGlobalProperties implements AgentConfigProvider, McpConfigProvider, SkillConfigProvider {

    private Map<String, AgentConfig> agents;
    private Map<String, McpServerConfig> mcps;
    private Map<String, SkillConfig> skills;

    @Override
    public AgentConfig getAgentConfig(String agentKey) {
        if (agents == null)
            return null;
        return agents.get(agentKey);
    }

    @Override
    public List<AgentConfig> getAllAgents() {
        if (agents == null)
            return Collections.emptyList();
        return new ArrayList<>(agents.values());
    }

    @Override
    public McpServerConfig getMcpConfig(String mcpKey) {
        if (mcps == null)
            return null;
        return mcps.get(mcpKey);
    }

    @Override
    public List<McpServerConfig> getAllMcps() {
        if (mcps == null)
            return Collections.emptyList();
        return new ArrayList<>(mcps.values());
    }

    @Override
    public SkillConfig getSkillConfig(String skillKey) {
        if (skills == null)
            return null;
        return skills.get(skillKey);
    }

    @Override
    public List<SkillConfig> getAllSkills() {
        if (skills == null)
            return Collections.emptyList();
        return new ArrayList<>(skills.values());
    }
}
