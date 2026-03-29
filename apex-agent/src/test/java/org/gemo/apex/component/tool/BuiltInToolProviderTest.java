package org.gemo.apex.component.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInToolProviderTest {

    @Test
    void initShouldNotRegisterLegacyStageTools() {
        BuiltInToolProvider provider = new BuiltInToolProvider();

        provider.init();

        List<String> toolNames = provider.getBuiltInTools().stream()
                .map(tool -> tool.getToolDefinition().name())
                .toList();
        assertTrue(toolNames.contains("ask_human"));
        assertTrue(toolNames.contains("write_plan_tool"));
        assertTrue(toolNames.contains("update_plan_tool"));
        assertFalse(toolNames.contains("direct_answer"));
        assertFalse(toolNames.contains("write_mode"));
    }
}
