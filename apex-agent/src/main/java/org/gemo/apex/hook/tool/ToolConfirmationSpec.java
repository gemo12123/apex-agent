package org.gemo.apex.hook.tool;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ToolConfirmationSpec {
    private String confirmationId;
    private String title;
    private String description;
    private String toolName;
    private String toolDisplayName;
    private String riskLevel;
    private boolean editable;
    private String confirmLabel;
    private String denyLabel;

    @Builder.Default
    private List<ToolConfirmationDisplayField> displayFields = List.of();

    @Builder.Default
    private List<ToolConfirmationEditableField> editableFields = List.of();

    public List<String> editableFieldKeys() {
        return editableFields.stream().map(ToolConfirmationEditableField::getKey).toList();
    }
}
