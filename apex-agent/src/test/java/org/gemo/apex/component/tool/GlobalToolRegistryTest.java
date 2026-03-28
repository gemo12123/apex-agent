package org.gemo.apex.component.tool;

import org.gemo.apex.service.AgentWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.tool.ToolCallback;

import java.util.Collections;
import java.util.List;

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
        assertTrue(tools.isEmpty(), "当没有配置 MCP 时应返回空列表");
    }

    @Test
    public void testGetMcpToolCallbacks_WithMissingConfig() {
        when(agentWorkspaceService.getMcpNames(anyString())).thenReturn(List.of("missing_mcp"));
        when(agentWorkspaceService.getMcpServerConfig(anyString())).thenReturn(null);

        List<ToolCallback> tools = globalToolRegistry.getMcpToolCallbacks("test_agent");
        assertTrue(tools.isEmpty(), "如果找不到对应的 server 配置，应当跳过并返回空");
    }
}
