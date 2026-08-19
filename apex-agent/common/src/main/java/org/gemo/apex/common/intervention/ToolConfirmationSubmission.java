package org.gemo.apex.common.intervention;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import java.util.Map;
import org.gemo.apex.common.support.DomainValues;

public record ToolConfirmationSubmission(
        String toolCallId,
        String confirmationId,
        ConfirmationDecision decision,
        Map<String, Object> updatedArguments)
        implements HumanSubmission {
    public ToolConfirmationSubmission {
        toolCallId = required(toolCallId, "toolCallId");
        confirmationId = required(confirmationId, "confirmationId");
        decision = nonNull(decision, "decision");
        updatedArguments = DomainValues.immutableMap(updatedArguments, "updatedArguments");
    }
}
