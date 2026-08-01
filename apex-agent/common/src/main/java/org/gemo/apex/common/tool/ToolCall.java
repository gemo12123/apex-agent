package org.gemo.apex.common.tool;

import org.gemo.apex.common.support.DomainValues;

import java.util.Map;

import static org.gemo.apex.common.support.DomainValues.nonNegative;
import static org.gemo.apex.common.support.DomainValues.required;

public record ToolCall(String toolCallId, String name, int ordinal, Map<String, Object> arguments,
                       Map<String, Object> metadata) {
    public ToolCall {
        toolCallId = required(toolCallId, "toolCallId");
        name = required(name, "name");
        nonNegative(ordinal, "ordinal");
        arguments = DomainValues.jsonMap(arguments, "arguments");
        metadata = DomainValues.jsonMap(metadata, "metadata");
    }
}
