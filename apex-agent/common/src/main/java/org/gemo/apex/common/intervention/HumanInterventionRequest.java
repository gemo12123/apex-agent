package org.gemo.apex.common.intervention;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = QuestionInterventionRequest.class, name = "QUESTION"),
        @JsonSubTypes.Type(value = ToolConfirmationInterventionRequest.class, name = "TOOL_CONFIRMATION")
})
public sealed interface HumanInterventionRequest
        permits QuestionInterventionRequest, ToolConfirmationInterventionRequest {
    String toolCallId();
}
