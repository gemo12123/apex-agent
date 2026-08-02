package org.gemo.apex.kit.intervention;

import org.gemo.apex.common.hook.context.PreToolCallContext;
import org.gemo.apex.common.intervention.ToolConfirmationInterventionRequest;
import org.gemo.apex.protocol.event.detail.EditableFieldInputType;
import org.gemo.apex.protocol.event.detail.ToolConfirmationDetail;
import org.gemo.apex.protocol.event.detail.ToolConfirmationDisplayField;
import org.gemo.apex.protocol.event.detail.ToolConfirmationEditableField;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ToolConfirmationSpecFactory {
    public ToolConfirmationInterventionRequest create(PreToolCallContext context) {
        Map<String, Object> options = context.binding().options();
        List<ToolConfirmationDisplayField> displayFields = displayFields(options.get("display-fields"),
                context.toolCall().arguments());
        List<ToolConfirmationEditableField> editableFields = editableFields(options.get("editable-fields"),
                context.toolCall().arguments());
        LinkedHashSet<String> editableKeys = new LinkedHashSet<>();
        editableFields.forEach(field -> editableKeys.add(field.getKey()));

        ToolConfirmationDetail detail = ToolConfirmationDetail.builder()
                .confirmationId(context.proposedInterventionId())
                .toolCallId(context.toolCall().toolCallId())
                .invocationId(context.invocationId())
                .toolName(context.toolCall().name())
                .toolDisplayName(option(options, "tool-display-name", context.toolCall().name()))
                .title(option(options, "title", "工具调用确认"))
                .description(option(options, "description", "请确认是否继续执行该工具调用。"))
                .riskLevel(option(options, "risk-level", "MEDIUM"))
                .editable(!editableFields.isEmpty())
                .confirmLabel(option(options, "confirm-label", "确认执行"))
                .denyLabel(option(options, "deny-label", "取消"))
                .displayFields(displayFields)
                .editableFields(editableFields)
                .build();
        return new ToolConfirmationInterventionRequest(context.toolCall().toolCallId(),
                context.proposedInterventionId(), context.invocationId(), context.toolCall().name(),
                detail, editableKeys);
    }

    private List<ToolConfirmationDisplayField> displayFields(Object rawFields, Map<String, Object> arguments) {
        List<Map<?, ?>> fields = fields(rawFields, "display-fields");
        Set<String> keys = new HashSet<>();
        List<ToolConfirmationDisplayField> result = new ArrayList<>(fields.size());
        for (Map<?, ?> field : fields) {
            String key = required(field.get("key"), "display-fields.key");
            requireArgument(arguments, key);
            if (!keys.add(key)) throw new IllegalArgumentException("display-fields.key 重复: " + key);
            result.add(ToolConfirmationDisplayField.builder()
                    .key(key)
                    .label(required(field.get("label"), "display-fields.label"))
                    .value(arguments.get(key))
                    .type(option(field, "type", "text"))
                    .build());
        }
        return List.copyOf(result);
    }

    private List<ToolConfirmationEditableField> editableFields(Object rawFields, Map<String, Object> arguments) {
        List<Map<?, ?>> fields = fields(rawFields, "editable-fields");
        Set<String> keys = new HashSet<>();
        List<ToolConfirmationEditableField> result = new ArrayList<>(fields.size());
        for (Map<?, ?> field : fields) {
            String key = required(field.get("key"), "editable-fields.key");
            requireArgument(arguments, key);
            if (!keys.add(key)) throw new IllegalArgumentException("editable-fields.key 重复: " + key);
            result.add(ToolConfirmationEditableField.builder()
                    .key(key)
                    .label(required(field.get("label"), "editable-fields.label"))
                    .inputType(EditableFieldInputType.fromWireValue(option(field, "input-type", "text")))
                    .value(arguments.get(key))
                    .required(booleanValue(field.get("required")))
                    .options(fieldOptions(field.get("options")))
                    .build());
        }
        return List.copyOf(result);
    }

    private List<Map<?, ?>> fields(Object rawFields, String name) {
        if (rawFields == null) return List.of();
        if (!(rawFields instanceof List<?> values)) throw new IllegalArgumentException(name + " 必须是数组");
        List<Map<?, ?>> fields = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> field)) throw new IllegalArgumentException(name + " 只能包含对象");
            fields.add(field);
        }
        return fields;
    }

    private List<Map<String, Object>> fieldOptions(Object rawOptions) {
        if (rawOptions == null) return List.of();
        if (!(rawOptions instanceof List<?> values)) throw new IllegalArgumentException("editable-fields.options 必须是数组");
        List<Map<String, Object>> options = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> option)) {
                throw new IllegalArgumentException("editable-fields.options 只能包含对象");
            }
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("label", required(option.get("label"), "editable-fields.options.label"));
            if (option.get("description") != null) normalized.put("description", option.get("description"));
            options.add(normalized);
        }
        return List.copyOf(options);
    }

    private void requireArgument(Map<String, Object> arguments, String key) {
        if (!arguments.containsKey(key)) throw new IllegalArgumentException("字段指向不存在的工具参数: " + key);
    }

    private String option(Map<?, ?> options, String key, String defaultValue) {
        Object value = options.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private String required(Object value, String field) {
        String text = value == null ? null : String.valueOf(value);
        if (text == null || text.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return text;
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }
}
