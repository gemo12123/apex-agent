package org.gemo.apex.common.agent;

import static org.gemo.apex.common.support.DomainValues.required;

public record AddAvailableTool(String toolName) implements AgentDefinitionOperation {
    public AddAvailableTool {
        toolName = required(toolName, "toolName");
    }
}
