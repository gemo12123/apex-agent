package org.gemo.apex.core.engine;

import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.service.AgentWorkspaceService;
import org.gemo.apex.skills.definition.skill.Skill;
import org.gemo.apex.skills.Skills;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class StagePromptBuilderTest {

    private AgentWorkspaceService agentWorkspaceService;

    private StagePromptBuilder stagePromptBuilder;

    @BeforeEach
    void setUp() {
        agentWorkspaceService = org.mockito.Mockito.mock(AgentWorkspaceService.class);
        MockitoAnnotations.openMocks(this);
        stagePromptBuilder = new StagePromptBuilder(agentWorkspaceService);
    }

    @Test
    void buildShouldMergeReactPromptSkillsToolsAndRules() {
        SuperAgentContext context = new SuperAgentContext();
        context.setAgentKey("agent-1");
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        context.setExecutionMode(ModeEnum.REACT);
        context.setSkills(Skills.from(Skill.builder()
                .name("demo")
                .description("demo description")
                .content("demo instructions")
                .build()));
        when(agentWorkspaceService.getReActPrompt("agent-1"))
                .thenReturn("skills={skills}\ntools={available_tools_desc}\ndate={date}");
        when(agentWorkspaceService.getAgentRules("agent-1")).thenReturn("NO_DELETE");

        String prompt = stagePromptBuilder.build(context, List.of(tool("meeting_tool", "meeting desc")));

        assertTrue(prompt.contains("<available_skills>"));
        assertTrue(prompt.contains("<name>demo</name>"));
        assertTrue(prompt.contains("<description>demo description</description>"));
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
