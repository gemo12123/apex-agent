package org.gemo.apex.common.hook.operation;

import org.gemo.apex.common.support.DomainValues;

import java.util.Map;

public record ToolCallPatch(Map<String, Object> arguments) {
    public ToolCallPatch { arguments = DomainValues.immutableMap(arguments, "arguments"); }
}
