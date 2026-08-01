package org.gemo.apex.common.model;

import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.support.DomainValues;
import org.gemo.apex.common.tool.ToolDefinition;

import java.util.List;
import java.util.Map;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.required;

public record ModelRequest(String systemPrompt, List<AgentMessageEntry> messages,
                           List<ToolDefinition> tools, Map<String, Object> options) {
    public ModelRequest {
        systemPrompt = required(systemPrompt, "systemPrompt");
        messages = immutableList(messages, "messages");
        tools = immutableList(tools, "tools");
        options = DomainValues.jsonMap(options, "options");
    }
}
