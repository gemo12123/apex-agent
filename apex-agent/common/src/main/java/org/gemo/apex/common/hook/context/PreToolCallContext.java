package org.gemo.apex.common.hook.context;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.intervention.HumanSubmission;
import org.gemo.apex.common.tool.ToolCall;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record PreToolCallContext(String sessionId, HookBinding binding, ToolCall toolCall,
                                 String invocationId, String proposedInterventionId,
                                 HumanSubmission humanSubmission) implements HookContextView {
    public PreToolCallContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        toolCall = nonNull(toolCall, "toolCall");
        invocationId = required(invocationId, "invocationId");
        proposedInterventionId = required(proposedInterventionId, "proposedInterventionId");
    }
}
