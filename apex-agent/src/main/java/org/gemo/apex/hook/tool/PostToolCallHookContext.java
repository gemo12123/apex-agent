package org.gemo.apex.hook.tool;

import lombok.Builder;
import lombok.Data;
import org.gemo.apex.context.SuperAgentContext;

import java.util.Map;

@Data
@Builder(toBuilder = true)
public class PostToolCallHookContext {
    private String agentKey;
    private String sessionId;
    private String userId;
    private String toolCallId;
    private String invocationId;
    private String toolName;
    private String rawArguments;
    private Map<String, Object> arguments;
    private Map<String, Object> hookOptions;
    private String originalResult;
    private String currentResult;
    private String hookSource;
    private SuperAgentContext superAgentContext;
}
