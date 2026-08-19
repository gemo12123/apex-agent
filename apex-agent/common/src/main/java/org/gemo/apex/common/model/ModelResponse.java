package org.gemo.apex.common.model;

import static org.gemo.apex.common.support.DomainValues.immutableList;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gemo.apex.common.exception.DomainInvariantException;
import org.gemo.apex.common.support.DomainValues;
import org.gemo.apex.common.tool.ToolCall;

public record ModelResponse(String text, List<ToolCall> toolCalls, Map<String, Object> metadata) {
    public ModelResponse {
        toolCalls = immutableList(toolCalls, "toolCalls");
        metadata = DomainValues.jsonMap(metadata, "metadata");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCall call = toolCalls.get(i);
            if (call.ordinal() != i) {
                throw new DomainInvariantException("toolCalls.ordinal 必须从 0 连续递增");
            }
            if (!ids.add(call.toolCallId())) {
                throw new DomainInvariantException("toolCallId 重复: " + call.toolCallId());
            }
        }
    }
}
