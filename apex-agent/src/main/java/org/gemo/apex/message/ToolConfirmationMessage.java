package org.gemo.apex.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.gemo.apex.constant.ContextKeyEnum;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.hook.tool.ToolConfirmationDisplayField;
import org.gemo.apex.hook.tool.ToolConfirmationEditableField;
import org.gemo.apex.hook.tool.ToolConfirmationSpec;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ToolConfirmationMessage extends AgentMessage {

    @JsonProperty("messages")
    private List<ToolConfirmationDetail> messages;

    public static ToolConfirmationMessage from(SuperAgentContext context, AssistantMessage.ToolCall toolCall,
            String invocationId, ToolConfirmationSpec spec) {
        return ToolConfirmationMessage.builder()
                .context(buildContext(context, toolCall, invocationId))
                .messages(List.of(ToolConfirmationDetail.builder()
                        .confirmationId(spec.getConfirmationId())
                        .toolCallId(toolCall.id())
                        .invocationId(invocationId)
                        .toolName(spec.getToolName())
                        .toolDisplayName(spec.getToolDisplayName())
                        .title(spec.getTitle())
                        .description(spec.getDescription())
                        .riskLevel(spec.getRiskLevel())
                        .hookSource(spec.getHookSource())
                        .editable(spec.isEditable())
                        .confirmLabel(spec.getConfirmLabel())
                        .denyLabel(spec.getDenyLabel())
                        .displayFields(spec.getDisplayFields())
                        .editableFields(spec.getEditableFields())
                        .build()))
                .build();
    }

    private static Map<String, Object> buildContext(SuperAgentContext context, AssistantMessage.ToolCall toolCall,
            String invocationId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(ContextKeyEnum.MODE.getKey(),
                context.getExecutionMode() != null ? context.getExecutionMode().getMode() : "");
        if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR && context.getCurrentStageId() != null) {
            payload.put(ContextKeyEnum.STAGE_ID.getKey(), context.getCurrentStageId());
        }
        payload.put(ContextKeyEnum.EXECUTOR.getKey(), toolCall.name());
        payload.put(ContextKeyEnum.INVOCATION_ID.getKey(), invocationId);
        return Map.copyOf(payload);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolConfirmationDetail {
        @JsonProperty("confirmation_id")
        private String confirmationId;

        @JsonProperty("tool_call_id")
        private String toolCallId;

        @JsonProperty("invocation_id")
        private String invocationId;

        @JsonProperty("tool_name")
        private String toolName;

        @JsonProperty("tool_display_name")
        private String toolDisplayName;

        @JsonProperty("title")
        private String title;

        @JsonProperty("description")
        private String description;

        @JsonProperty("risk_level")
        private String riskLevel;

        @JsonProperty("hook_source")
        private String hookSource;

        @JsonProperty("editable")
        private boolean editable;

        @JsonProperty("confirm_label")
        private String confirmLabel;

        @JsonProperty("deny_label")
        private String denyLabel;

        @JsonProperty("display_fields")
        private List<ToolConfirmationDisplayField> displayFields;

        @JsonProperty("editable_fields")
        private List<ToolConfirmationEditableField> editableFields;
    }
}
