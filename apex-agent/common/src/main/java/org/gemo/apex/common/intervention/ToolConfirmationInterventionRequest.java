package org.gemo.apex.common.intervention;

import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.protocol.event.detail.ToolConfirmationDetail;

import java.util.Set;

import static org.gemo.apex.common.support.DomainValues.*;

public record ToolConfirmationInterventionRequest(String toolCallId, String confirmationId,
                                                  String invocationId, String toolName,
                                                  ToolConfirmationDetail presentation,
                                                  Set<String> editableArgumentKeys)
        implements HumanInterventionRequest {
    public ToolConfirmationInterventionRequest {
        toolCallId = required(toolCallId, "toolCallId");
        confirmationId = required(confirmationId, "confirmationId");
        invocationId = required(invocationId, "invocationId");
        toolName = required(toolName, "toolName");
        presentation = nonNull(presentation, "presentation");
        if (!toolCallId.equals(presentation.getToolCallId())
                || !confirmationId.equals(presentation.getConfirmationId())
                || !invocationId.equals(presentation.getInvocationId())
                || !toolName.equals(presentation.getToolName())) {
            throw new IllegalArgumentException("presentation 标识必须与确认请求一致");
        }
        presentation = JsonUtils.deepCopy(presentation, ToolConfirmationDetail.class);
        editableArgumentKeys = immutableNames(editableArgumentKeys, "editableArgumentKeys");
    }

    @Override
    public ToolConfirmationDetail presentation() {
        return JsonUtils.deepCopy(presentation, ToolConfirmationDetail.class);
    }
}
