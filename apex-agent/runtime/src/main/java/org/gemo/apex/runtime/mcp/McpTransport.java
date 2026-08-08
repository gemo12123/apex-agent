package org.gemo.apex.runtime.mcp;

import org.gemo.apex.common.tool.ToolDefinition;

import java.util.*;

public interface McpTransport extends AutoCloseable {
    void connect();

    List<ToolDefinition> listTools();

    McpCallHandle call(String n, Map<String, Object> a);
}
