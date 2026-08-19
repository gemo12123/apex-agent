package org.gemo.apex.common.tool;

import static org.gemo.apex.common.support.DomainValues.required;

import java.util.Map;
import org.gemo.apex.common.support.DomainValues;

public record ToolDefinition(
        String name, String description, String inputSchemaJson, Map<String, Object> metadata) {
    public ToolDefinition {
        name = required(name, "name");
        description = required(description, "description");
        inputSchemaJson = required(inputSchemaJson, "inputSchemaJson");
        metadata = DomainValues.jsonMap(metadata, "metadata");
    }
}
