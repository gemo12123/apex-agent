package org.gemo.apex.skills.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.gemo.apex.skills.support.Validators;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Map;

public abstract class AbstractSkillToolCallback implements ToolCallback {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolDefinition toolDefinition;
    private final ToolMetadata toolMetadata;

    protected AbstractSkillToolCallback(ToolDefinition toolDefinition, ToolMetadata toolMetadata) {
        this.toolDefinition = Validators.notNull(toolDefinition, "toolDefinition");
        this.toolMetadata = Validators.notNull(toolMetadata, "toolMetadata");
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return toolMetadata;
    }

    protected Map<String, Object> parseArguments(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse tool arguments: " + json, e);
        }
    }

    protected String getRequiredArgument(String argumentName, Map<String, Object> arguments) {
        if (arguments == null || !arguments.containsKey(argumentName) || arguments.get(argumentName) == null) {
            throw new IllegalArgumentException("Missing required tool argument '" + argumentName + "'");
        }
        return arguments.get(argumentName).toString();
    }

    protected static String singleStringInputSchema(String parameterName, String parameterDescription) {
        ObjectNode schema = OBJECT_MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode property = properties.putObject(parameterName);
        property.put("type", "string");
        property.put("description", parameterDescription);
        schema.putArray("required").add(parameterName);
        schema.put("additionalProperties", false);
        return schema.toString();
    }

    protected static String twoStringInputSchema(String firstName, String firstDescription, String secondName,
            String secondDescription) {
        ObjectNode schema = OBJECT_MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode first = properties.putObject(firstName);
        first.put("type", "string");
        first.put("description", firstDescription);
        ObjectNode second = properties.putObject(secondName);
        second.put("type", "string");
        second.put("description", secondDescription);
        schema.putArray("required").add(firstName).add(secondName);
        schema.put("additionalProperties", false);
        return schema.toString();
    }
}
