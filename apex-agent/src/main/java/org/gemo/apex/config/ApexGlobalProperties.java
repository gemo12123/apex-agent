package org.gemo.apex.config;

import lombok.Data;
import org.gemo.apex.config.model.AgentConfig;
import org.gemo.apex.config.model.McpServerConfig;
import org.gemo.apex.config.model.SkillConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Apex Global Configuration Properties.
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.global")
public class ApexGlobalProperties {

    private Map<String, AgentConfig> agents;
    private Map<String, McpServerConfig> mcps;
    private Map<String, SkillConfig> skills;
}
