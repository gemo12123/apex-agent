package org.gemo.apex.common.model;

import org.gemo.apex.common.support.DomainValues;
import org.gemo.apex.common.tool.ToolCall;

import java.util.List;
import java.util.Map;

import static org.gemo.apex.common.support.DomainValues.immutableList;

public record ModelStreamChunk(String textDelta, List<ToolCall> toolCalls,
                               Map<String, Object> metadata, boolean completed) {
    public ModelStreamChunk {
        toolCalls = immutableList(toolCalls, "toolCalls");
        metadata = DomainValues.jsonMap(metadata, "metadata");
    }
}
