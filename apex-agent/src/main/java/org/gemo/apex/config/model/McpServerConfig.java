package org.gemo.apex.config.model;

import lombok.Data;
import java.util.List;

/**
 * Global configuration model for an MCP Server connection
 */
@Data
public class McpServerConfig {

    /** The executable command (e.g. npx, node, python) */
    private String command;

    /** Arguments for the command */
    private List<String> args;

    /** Request timeout in seconds */
    private Integer timeoutSeconds = 60;
}
