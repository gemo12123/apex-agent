package org.gemo.apex.core.engine;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.service.AgentWorkspaceService;
import org.gemo.apex.tool.skills.CustomSkillsTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class StagePromptBuilderTest {

    @Mock
    private AgentWorkspaceService agentWorkspaceService;

    @Mock
    private CustomSkillsTool customSkillsTool;

    private StagePromptBuilder stagePromptBuilder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        stagePromptBuilder = new StagePromptBuilder(agentWorkspaceService);
    }

    @Test
    void buildShouldMergeTemplateSkillsToolsAndRules() {
        SuperAgentContext context = new SuperAgentContext();
        context.setAgentKey("agent-1");
        context.setCurrentStage(SuperAgentContext.Stage.THINKING);
        context.setCustomSkillsTool(customSkillsTool);
        when(customSkillsTool.getSkillsXml()).thenReturn("<skill>demo</skill>");
        when(agentWorkspaceService.getThinkingPrompt("agent-1"))
                .thenReturn("skills={skills}\ntools={available_tools_desc}\ndate={date}");
        when(agentWorkspaceService.getAgentRules("agent-1")).thenReturn("NO_DELETE");

        String prompt = stagePromptBuilder.build(context, List.of(tool("meeting_tool", "meeting desc")));

        assertTrue(prompt.contains("<skill>demo</skill>"));
        assertTrue(prompt.contains("- meeting_tool: meeting desc"));
        assertTrue(prompt.contains("NO_DELETE"));
    }

    private ToolCallback tool(String name, String description) {
        ToolDefinition definition = DefaultToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema("{}")
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().build();
            }

            @Override
            public String call(String toolInput) {
                return "";
            }
        };
    }
}
