package org.gemo.apex.hook.tool.builtin;

import org.gemo.apex.hook.tool.PreToolCallHookContext;
import org.gemo.apex.hook.tool.PreToolCallHookResult;
import org.gemo.apex.hook.tool.ToolConfirmationEditableField;
import org.gemo.apex.hook.tool.ToolConfirmationSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolConfirmHookTest {

    private final ToolConfirmHook hook = new ToolConfirmHook();

    @Test
    void applyShouldBuildConfirmationSpecFromHookOptions() {
        Map<String, Object> hookOptions = Map.of(
                "title", "\u9884\u8ba2\u4f1a\u8bae\u5ba4\u524d\u786e\u8ba4",
                "description", "\u8bf7\u786e\u8ba4\u4f1a\u8bae\u4fe1\u606f\u3002",
                "tool-display-name", "\u4f1a\u8bae\u5ba4\u52a9\u624b",
                "confirm-label", "\u786e\u8ba4\u6267\u884c",
                "deny-label", "\u53d6\u6d88",
                "display-fields", List.of(
                        Map.of("key", "room", "label", "\u4f1a\u8bae\u5ba4"),
                        Map.of("key", "date", "label", "\u65e5\u671f")),
                "editable-fields", List.of(
                        Map.of(
                                "key", "room",
                                "label", "\u4f1a\u8bae\u5ba4",
                                "input-type", "single-select",
                                "required", true,
                                "options", List.of(
                                        Map.of("label", "A1001"),
                                        Map.of("label", "B2001")))));

        PreToolCallHookResult result = hook.apply(PreToolCallHookContext.builder()
                .toolName("meeting_tool")
                .arguments(Map.of(
                        "room", "A1001",
                        "date", "2026-04-22"))
                .hookOptions(hookOptions)
                .build());

        assertEquals(PreToolCallHookResult.Outcome.REQUEST_CONFIRMATION, result.getOutcome());

        ToolConfirmationSpec spec = result.getConfirmationSpec();
        assertEquals("\u9884\u8ba2\u4f1a\u8bae\u5ba4\u524d\u786e\u8ba4", spec.getTitle());
        assertEquals("\u4f1a\u8bae\u5ba4\u52a9\u624b", spec.getToolDisplayName());
        assertEquals("\u786e\u8ba4\u6267\u884c", spec.getConfirmLabel());
        assertEquals("\u53d6\u6d88", spec.getDenyLabel());
        assertEquals(2, spec.getDisplayFields().size());
        assertTrue(spec.isEditable());

        ToolConfirmationEditableField editableField = spec.getEditableFields().getFirst();
        assertEquals("room", editableField.getKey());
        assertEquals("\u4f1a\u8bae\u5ba4", editableField.getLabel());
        assertTrue(Boolean.TRUE.equals(editableField.getRequired()));
    }
}
