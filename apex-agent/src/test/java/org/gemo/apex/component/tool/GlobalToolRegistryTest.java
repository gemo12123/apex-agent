package org.gemo.apex.component.tool;

import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.service.AgentWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.tool.ToolCallback;

import java.lang.reflect.Method;
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
    private AgentWorkspaceService agentWorkspaceService;

    @InjectMocks
    private GlobalToolRegistry globalToolRegistry;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetMcpToolCallbacks_NoMcps() {
        when(agentWorkspaceService.getMcpNames(anyString())).thenReturn(Collections.emptyList());

        List<ToolCallback> tools = globalToolRegistry.getMcpToolCallbacks("test_agent");
        assertTrue(tools.isEmpty());
    }

    @Test
    public void testGetMcpToolCallbacks_WithMissingConfig() {
        when(agentWorkspaceService.getMcpNames(anyString())).thenReturn(List.of("missing_mcp"));
        when(agentWorkspaceService.getMcpServerConfig(anyString())).thenReturn(null);

        List<ToolCallback> tools = globalToolRegistry.getMcpToolCallbacks("test_agent");
        assertTrue(tools.isEmpty());
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
}
