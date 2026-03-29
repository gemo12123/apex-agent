package org.gemo.apex.core.engine;

import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.domain.Plan;
import org.gemo.apex.domain.Stage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageToolResolverTest {

    private final StageToolResolver resolver = new StageToolResolver();

    @Test
    void resolveShouldAllowOnlySkillAndDirectAnswerInThinking() {
        SuperAgentContext context = baseContext();
        context.setCurrentStage(SuperAgentContext.Stage.THINKING);

        StageToolPlan plan = resolver.resolve(context);

        assertEquals(List.of("activate_skill", "direct_answer"),
                plan.callableTools().stream().map(tool -> tool.getToolDefinition().name()).toList());
        assertEquals(List.of("ask_human", "meeting_tool"),
                plan.promptDescribedTools().stream().map(tool -> tool.getToolDefinition().name()).toList());
    }

    @Test
    void resolveShouldAllowOnlyWriteModeInModeConfirmation() {
        SuperAgentContext context = baseContext();
        context.setCurrentStage(SuperAgentContext.Stage.MODE_CONFIRMATION);

        StageToolPlan plan = resolver.resolve(context);

        assertEquals(List.of("write_mode"),
                plan.callableTools().stream().map(tool -> tool.getToolDefinition().name()).toList());
    }

    @Test
    void resolveShouldForceWritePlanWhenPlanExecutorHasNoPlan() {
        SuperAgentContext context = baseContext();
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        context.setExecutionMode(ModeEnum.PLAN_EXECUTOR);

        StageToolPlan plan = resolver.resolve(context);

        assertEquals(List.of("write_plan_tool"),
                plan.callableTools().stream().map(tool -> tool.getToolDefinition().name()).toList());
    }

    @Test
    void resolveShouldAllowReplanAndBusinessToolsWhenPlanExecutorHasPlan() {
        SuperAgentContext context = baseContext();
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        context.setExecutionMode(ModeEnum.PLAN_EXECUTOR);
        Plan executionPlan = new Plan();
        executionPlan.setStages(new ArrayList<>(List.of(new Stage())));
        context.setPlan(executionPlan);

        StageToolPlan plan = resolver.resolve(context);

        assertEquals(List.of("ask_human", "update_plan_tool", "meeting_tool"),
                plan.callableTools().stream().map(tool -> tool.getToolDefinition().name()).toList());
    }

    private SuperAgentContext baseContext() {
        SuperAgentContext context = new SuperAgentContext();
        context.setAvailableTools(List.of(
                tool("activate_skill"),
                tool("direct_answer"),
                tool("ask_human"),
                tool("write_mode"),
                tool("write_plan_tool"),
                tool("update_plan_tool"),
                tool("meeting_tool")));
        return context;
    }

    private ToolCallback tool(String name) {
        ToolDefinition definition = DefaultToolDefinition.builder().name(name).description(name).inputSchema("{}").build();
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
