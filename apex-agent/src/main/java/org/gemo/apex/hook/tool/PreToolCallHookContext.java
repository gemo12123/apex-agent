package org.gemo.apex.hook.tool;

import lombok.Builder;
import lombok.Data;
import org.gemo.apex.context.SuperAgentContext;

import java.util.Map;
import java.util.Set;

@Data
@Builder(toBuilder = true)
public class PreToolCallHookContext {
    private String agentKey;
    private String sessionId;
    private String userId;
    private String toolCallId;
    private String invocationId;
    private String toolName;
    private String toolDescription;
    private String toolType;
    private String rawArguments;
    private Map<String, Object> arguments;
    private Map<String, Object> hookOptions;
    private Set<String> skippedHookBeans;
    private SuperAgentContext superAgentContext;
}
