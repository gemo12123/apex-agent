package org.gemo.apex.config.provider;

import org.gemo.apex.config.model.McpServerConfig;
import java.util.List;

/**
 * Provider interface for MCP Server configurations.
 * Allows decoupled configuration sources for MCP definitions.
 */
public interface McpConfigProvider {

    /**
     * Get the connection configuration for a specific MCP Server.
     * 
     * @param mcpKey the unique identifier for the MCP server
     * @return the configuration, or null if not found
     */
    McpServerConfig getMcpConfig(String mcpKey);

    /**
     * Get a list of all configured MCP servers.
     * 
     * @return list of MCP server configurations
     */
    List<McpServerConfig> getAllMcps();
}
