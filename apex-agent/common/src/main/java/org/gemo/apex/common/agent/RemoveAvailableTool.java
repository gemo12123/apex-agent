package org.gemo.apex.common.agent;

import static org.gemo.apex.common.support.DomainValues.required;

public record RemoveAvailableTool(String toolName) implements AgentDefinitionOperation {
    public RemoveAvailableTool { toolName = required(toolName, "toolName"); }
}
