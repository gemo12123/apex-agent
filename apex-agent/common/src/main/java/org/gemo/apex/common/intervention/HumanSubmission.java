package org.gemo.apex.common.intervention;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = QuestionSubmission.class, name = "QUESTION"),
        @JsonSubTypes.Type(value = ToolConfirmationSubmission.class, name = "TOOL_CONFIRMATION")
})
public sealed interface HumanSubmission permits QuestionSubmission, ToolConfirmationSubmission {
    String toolCallId();
}
