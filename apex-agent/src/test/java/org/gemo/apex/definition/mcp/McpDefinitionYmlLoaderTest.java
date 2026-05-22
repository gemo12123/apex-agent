package org.gemo.apex.definition.mcp;

import org.gemo.apex.config.ApexGlobalProperties;
import org.gemo.apex.config.model.McpServerConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class McpDefinitionYmlLoaderTest {

    @Test
    void loadShouldReturnConfiguredMcpDefinition() {
        ApexGlobalProperties properties = new ApexGlobalProperties();
        McpServerConfig config = new McpServerConfig();
        config.setCommand("node");
        config.setArgs(List.of("server.js"));
        config.setTimeoutSeconds(90);
        properties.setMcps(Map.of("meeting-server", config));

        IMcpDefinitionLoader loader = new McpDefinitionYmlLoader(properties);

        McpServerConfig loaded = loader.load("meeting-server");

        assertEquals("node", loaded.getCommand());
        assertEquals(List.of("server.js"), loaded.getArgs());
        assertEquals(90, loaded.getTimeoutSeconds());
    }

    @Test
    void loadShouldReturnNullForUnknownMcp() {
        IMcpDefinitionLoader loader = new McpDefinitionYmlLoader(new ApexGlobalProperties());

        assertNull(loader.load("missing"));
    }
}
