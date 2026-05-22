package org.gemo.apex.component.tool;

import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.config.model.McpServerConfig;
import org.gemo.apex.config.model.SkillConfig;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.definition.agent.AgentDefinition;
import org.gemo.apex.definition.agent.IAgentDefinitionLoader;
import org.gemo.apex.definition.mcp.IMcpDefinitionLoader;
import org.gemo.apex.definition.skill.ISkillDefinitionLoader;
import org.gemo.apex.skills.Skills;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.tool.ToolCallback;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class GlobalToolRegistryTest {

    @Mock
    private IAgentDefinitionLoader agentDefinitionLoader;

    @Mock
    private IMcpDefinitionLoader mcpDefinitionLoader;

    @Mock
    private ISkillDefinitionLoader skillDefinitionLoader;

    @InjectMocks
    private GlobalToolRegistry globalToolRegistry;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetMcpToolCallbacks_NoMcps() {
        when(agentDefinitionLoader.load(anyString())).thenReturn(definition(List.of(), List.of(), List.of()));

        List<ToolCallback> tools = globalToolRegistry.getMcpToolCallbacks("test_agent");
        assertTrue(tools.isEmpty());
    }

    @Test
    public void testGetMcpToolCallbacks_WithMissingConfig() {
        when(agentDefinitionLoader.load("test_agent"))
                .thenReturn(definition(List.of("missing_mcp"), List.of(), List.of()));
        when(mcpDefinitionLoader.load(anyString())).thenReturn(null);

        List<ToolCallback> tools = globalToolRegistry.getMcpToolCallbacks("test_agent");
        assertTrue(tools.isEmpty());
    }

    @Test
    public void testGetSkillsTool_ShouldLoadDirectChildSkills() throws IOException {
        Path skillDir = tempDir.resolve("meeting");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: meeting-skill
                description: Meeting workflow
                ---

                Follow the meeting workflow
                """);

        when(agentDefinitionLoader.load("test_agent"))
                .thenReturn(definition(List.of(), List.of(), List.of("meeting-skill")));
        SkillConfig config = new SkillConfig();
        config.setDir(skillDir.toString());
        when(skillDefinitionLoader.load("meeting-skill")).thenReturn(config);

        Skills skills = globalToolRegistry.getSkillsTool("test_agent");

        assertEquals("activate_skill", skills.toolCallbacks()[0].getToolDefinition().name());
        assertTrue(skills.formatAvailableSkills().contains("<name>meeting-skill</name>"));
    }

    @Test
    public void buildToolContextToMcpMetaConverterShouldOnlyExposeSerializableSessionSnapshot() throws Exception {
        Method method = GlobalToolRegistry.class.getDeclaredMethod("buildToolContextToMcpMetaConverter");
        method.setAccessible(true);
        ToolContextToMcpMetaConverter converter = (ToolContextToMcpMetaConverter) method.invoke(globalToolRegistry);

        SuperAgentContext runtimeContext = new SuperAgentContext();
        runtimeContext.setSessionId("session-1");

        Map<String, Object> snapshot = Map.of(
                "sessionId", "session-1",
                "executionStatus", "IN_PROGRESS");
        ToolContext toolContext = new ToolContext(Map.of(
                ToolContextKeys.SESSION_CONTEXT, runtimeContext,
                "MCP_SESSION_CONTEXT", snapshot,
                ToolContext.TOOL_CALL_HISTORY, List.of("history"),
                "otherKey", "value"));

        Map<String, Object> meta = converter.convert(toolContext);

        assertEquals(1, meta.size());
        assertEquals(snapshot, meta.get(ToolContextKeys.SESSION_CONTEXT));
        assertNotSame(runtimeContext, meta.get(ToolContextKeys.SESSION_CONTEXT));
        assertFalse(meta.containsKey("MCP_SESSION_CONTEXT"));
        assertFalse(meta.containsKey(ToolContext.TOOL_CALL_HISTORY));
        assertFalse(meta.containsKey("otherKey"));
    }

    private AgentDefinition definition(List<String> mcpNames, List<String> subAgentNames, List<String> skillNames) {
        return new AgentDefinition(
                "test_agent",
                ModeEnum.REACT,
                mcpNames,
                subAgentNames,
                skillNames,
                AgentHooksConfig.empty(),
                "",
                "",
                "",
                "");
    }
}
