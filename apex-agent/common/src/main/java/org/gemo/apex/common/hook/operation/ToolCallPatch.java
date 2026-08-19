package org.gemo.apex.common.hook.operation;

import java.util.Map;
import org.gemo.apex.common.support.DomainValues;

public record ToolCallPatch(Map<String, Object> arguments) {
    public ToolCallPatch {
        arguments = DomainValues.immutableMap(arguments, "arguments");
    }
}
