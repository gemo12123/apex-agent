package org.gemo.apex.common.hook.operation;

import static org.gemo.apex.common.support.DomainValues.required;

import java.util.Map;
import org.gemo.apex.common.support.DomainValues;

public record ToolResultPatch(String content, Map<String, Object> metadata) {
    public ToolResultPatch {
        content = required(content, "content");
        metadata = DomainValues.immutableMap(metadata, "metadata");
    }
}
