package org.gemo.apex.common.model;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.required;

import java.util.List;
import java.util.Map;
import org.gemo.apex.common.agent.PrefixDeveloperMessage;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.support.DomainValues;
import org.gemo.apex.common.tool.ToolDefinition;

public record ModelRequest(
        String systemPrompt,
        List<PrefixDeveloperMessage> prefixDeveloperMessages,
        List<AgentMessageEntry> messages,
        List<ToolDefinition> tools,
        Map<String, Object> options) {
    public ModelRequest(
            String systemPrompt,
            List<AgentMessageEntry> messages,
            List<ToolDefinition> tools,
            Map<String, Object> options) {
        this(systemPrompt, List.of(), messages, tools, options);
    }

    public ModelRequest {
        systemPrompt = required(systemPrompt, "systemPrompt");
        prefixDeveloperMessages = immutableList(prefixDeveloperMessages, "prefixDeveloperMessages");
        messages = immutableList(messages, "messages");
        tools = immutableList(tools, "tools");
        options = DomainValues.jsonMap(options, "options");
    }

    public ModelRequest withoutPrefixDeveloperMessages() {
        if (prefixDeveloperMessages.isEmpty()) {
            return this;
        }
        return new ModelRequest(systemPrompt, List.of(), messages, tools, options);
    }
}
