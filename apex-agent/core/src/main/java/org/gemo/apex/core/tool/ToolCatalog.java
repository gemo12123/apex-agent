package org.gemo.apex.core.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gemo.apex.core.exception.InvalidAgentDefinitionException;
import org.gemo.apex.extension.tool.AgentTool;

public final class ToolCatalog {
    private final Map<String, AgentTool> tools;

    public ToolCatalog(List<AgentTool> tools) {
        Map<String, AgentTool> indexed = new LinkedHashMap<>();
        for (AgentTool tool : List.copyOf(tools)) {
            String name = tool.definition().name();
            if (indexed.putIfAbsent(name, tool) != null) {
                throw new InvalidAgentDefinitionException("工具名称重复: " + name);
            }
        }
        this.tools = Map.copyOf(indexed);
    }

    public AgentTool require(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new InvalidAgentDefinitionException("工具无法解析: " + name);
        }
        return tool;
    }

    public AgentTool find(String name) {
        return tools.get(name);
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public List<AgentTool> ordered() {
        return List.copyOf(tools.values());
    }
}
