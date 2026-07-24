package org.gemo.apex.hook.lifecycle;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@Jacksonized
public class ToolCallRecord {
    private String toolCallId;
    private String invocationId;
    private String toolName;
    @Builder.Default
    private Map<String, Object> arguments = new LinkedHashMap<>();
    private String originalResult;
    private String finalResult;
    private boolean succeeded;
    private HookFlowAction action;
    private String error;
}
