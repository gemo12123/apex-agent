package org.gemo.apex.definition.mcp;

import org.gemo.apex.config.ApexGlobalProperties;
import org.gemo.apex.config.model.McpServerConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class McpDefinitionYmlLoader implements IMcpDefinitionLoader {

    private final ApexGlobalProperties apexGlobalProperties;

    public McpDefinitionYmlLoader(ApexGlobalProperties apexGlobalProperties) {
        this.apexGlobalProperties = apexGlobalProperties;
    }

    @Override
    public McpServerConfig load(String mcpKey) {
        Map<String, McpServerConfig> mcps = apexGlobalProperties.getMcps();
        return mcps == null ? null : mcps.get(mcpKey);
    }
}
