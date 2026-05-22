package org.gemo.apex.definition.mcp;

import org.gemo.apex.config.model.McpServerConfig;

public interface IMcpDefinitionLoader {

    McpServerConfig load(String mcpKey);
}
