package org.gemo.apex.common.model;

import static org.gemo.apex.common.support.DomainValues.immutableList;

import java.util.List;
import java.util.Map;
import org.gemo.apex.common.support.DomainValues;
import org.gemo.apex.common.tool.ToolCall;

public record ModelStreamChunk(
        String textDelta,
        List<ToolCall> toolCalls,
        Map<String, Object> metadata,
        boolean completed) {
    public ModelStreamChunk {
        toolCalls = immutableList(toolCalls, "toolCalls");
        metadata = DomainValues.jsonMap(metadata, "metadata");
    }
}
