package org.gemo.apex.protocol.event.detail;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "interaction_type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AskHumanInterventionDetail.class, name = "ASK_HUMAN"),
    @JsonSubTypes.Type(value = ToolConfirmationDetail.class, name = "TOOL_CONFIRMATION")
})
public sealed interface HumanInterventionDetail
        permits AskHumanInterventionDetail, ToolConfirmationDetail {
    String getToolCallId();
}
