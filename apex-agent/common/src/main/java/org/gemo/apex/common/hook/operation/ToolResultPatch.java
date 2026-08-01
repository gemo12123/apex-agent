package org.gemo.apex.common.hook.operation;

import org.gemo.apex.common.support.DomainValues;

import java.util.Map;

import static org.gemo.apex.common.support.DomainValues.required;

public record ToolResultPatch(String content, Map<String, Object> metadata) {
    public ToolResultPatch {
        content = required(content, "content");
        metadata = DomainValues.immutableMap(metadata, "metadata");
    }
}
