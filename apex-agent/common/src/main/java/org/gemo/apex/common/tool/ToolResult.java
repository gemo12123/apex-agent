package org.gemo.apex.common.tool;

import org.gemo.apex.common.support.DomainValues;

import java.util.Map;

import static org.gemo.apex.common.support.DomainValues.required;

public record ToolResult(String toolCallId, String toolName, String content, Map<String, Object> metadata) {
    public ToolResult {
        toolCallId = required(toolCallId, "toolCallId");
        toolName = required(toolName, "toolName");
        content = required(content, "content");
        metadata = DomainValues.jsonMap(metadata, "metadata");
    }
}
