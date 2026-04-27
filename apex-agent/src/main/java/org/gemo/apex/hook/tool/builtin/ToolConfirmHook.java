package org.gemo.apex.hook.tool.builtin;

import org.gemo.apex.hook.tool.EditableFieldInputType;
import org.gemo.apex.hook.tool.PreToolCallHook;
import org.gemo.apex.hook.tool.PreToolCallHookContext;
import org.gemo.apex.hook.tool.PreToolCallHookResult;
import org.gemo.apex.hook.tool.ToolConfirmationDisplayField;
import org.gemo.apex.hook.tool.ToolConfirmationEditableField;
import org.gemo.apex.hook.tool.ToolConfirmationSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component("toolConfirmHook")
public class ToolConfirmHook implements PreToolCallHook {

    @Override
    public PreToolCallHookResult apply(PreToolCallHookContext context) {
        List<ToolConfirmationDisplayField> displayFields = resolveDisplayFields(context);
        List<ToolConfirmationEditableField> editableFields = resolveEditableFields(context);

        return PreToolCallHookResult.requestConfirmation(ToolConfirmationSpec.builder()
                .confirmationId(UUID.randomUUID().toString())
                .title(option(context, "title", "工具调用确认"))
                .description(option(context, "description", "请确认是否继续执行该工具调用。"))
                .toolName(context.getToolName())
                .toolDisplayName(option(context, "tool-display-name", context.getToolName()))
                .riskLevel(option(context, "risk-level", "MEDIUM"))
                .editable(!editableFields.isEmpty())
                .confirmLabel(option(context, "confirm-label", "确认执行"))
                .denyLabel(option(context, "deny-label", "取消"))
                .displayFields(displayFields)
                .editableFields(editableFields)
                .build());
    }

    @SuppressWarnings("unchecked")
    private List<ToolConfirmationDisplayField> resolveDisplayFields(PreToolCallHookContext context) {
        Object rawFields = context.getHookOptions() != null ? context.getHookOptions().get("display-fields") : null;
        if (!(rawFields instanceof List<?> fields)) {
            return List.of();
        }

        return fields.stream()
                .filter(Map.class::isInstance)
                .map(field -> (Map<?, ?>) field)
                .map(field -> ToolConfirmationDisplayField.builder()
                        .key(stringValue(field.get("key")))
                        .label(stringValue(field.get("label")))
                        .value(context.getArguments() != null ? context.getArguments().get(stringValue(field.get("key")))
                                : null)
                        .type(field.get("type") != null ? stringValue(field.get("type")) : "text")
                        .build())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<ToolConfirmationEditableField> resolveEditableFields(PreToolCallHookContext context) {
        Object rawFields = context.getHookOptions() != null ? context.getHookOptions().get("editable-fields") : null;
        if (!(rawFields instanceof List<?> fields)) {
            return List.of();
        }

        return fields.stream()
                .filter(Map.class::isInstance)
                .map(field -> (Map<?, ?>) field)
                .map(field -> ToolConfirmationEditableField.builder()
                        .key(stringValue(field.get("key")))
                        .label(stringValue(field.get("label")))
                        .inputType(EditableFieldInputType.fromWireValue(stringValue(field.get("input-type"))))
                        .value(context.getArguments() != null ? context.getArguments().get(stringValue(field.get("key")))
                                : null)
                        .required(field.get("required") instanceof Boolean bool ? bool
                                : Boolean.parseBoolean(stringValue(field.get("required"))))
                        .options(resolveOptions(field.get("options")))
                        .build())
                .toList();
    }

    private List<Map<String, Object>> resolveOptions(Object rawOptions) {
        if (!(rawOptions instanceof List<?> options)) {
            return List.of();
        }

        return options.stream()
                .filter(Map.class::isInstance)
                .map(option -> (Map<?, ?>) option)
                .map(option -> {
                    LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
                    normalized.put("label", option.get("label"));
                    if (option.get("description") != null) {
                        normalized.put("description", option.get("description"));
                    }
                    return (Map<String, Object>) normalized;
                })
                .toList();
    }

    private String option(PreToolCallHookContext context, String key, String defaultValue) {
        if (context.getHookOptions() == null || context.getHookOptions().get(key) == null) {
            return defaultValue;
        }
        return stringValue(context.getHookOptions().get(key));
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }
}
