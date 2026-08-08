package org.gemo.apex.protocol.event.detail;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class ToolConfirmationDetail implements HumanInterventionDetail {
    @JsonProperty("confirmation_id") private String confirmationId;
    @JsonProperty("tool_call_id") private String toolCallId;
    @JsonProperty("invocation_id") private String invocationId;
    @JsonProperty("tool_name") private String toolName;
    @JsonProperty("tool_display_name") private String toolDisplayName;
    @JsonProperty("title") private String title;
    @JsonProperty("description") private String description;
    @JsonProperty("risk_level") private String riskLevel;
    @JsonProperty("editable") private boolean editable;
    @JsonProperty("confirm_label") private String confirmLabel;
    @JsonProperty("deny_label") private String denyLabel;
    @JsonProperty("display_fields") private List<ToolConfirmationDisplayField> displayFields;
    @JsonProperty("editable_fields") private List<ToolConfirmationEditableField> editableFields;
}
