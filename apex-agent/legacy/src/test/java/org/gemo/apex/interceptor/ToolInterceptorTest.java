package org.gemo.apex.interceptor;

import org.gemo.apex.component.interceptor.ToolInterceptor;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.TaskStatus;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.domain.Plan;
import org.gemo.apex.domain.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolInterceptorTest {

    private ToolInterceptor toolInterceptor;
    private SuperAgentContext context;

    @BeforeEach
    void setUp() {
        toolInterceptor = new ToolInterceptor();
        context = new SuperAgentContext();
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
    }

    @Test
    void executionStageShouldRejectPlanToolsInReactMode() {
        context.setExecutionMode(ModeEnum.REACT);
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall("1", "function", "write_plan_tool", "{}"),
                new AssistantMessage.ToolCall("2", "function", "update_plan_tool", "{}"));

        ToolResponseMessage response = toolInterceptor.interceptIllegalToolCalls(context, toolCalls);

        assertNotNull(response);
        assertEquals(2, response.getResponses().size());
        assertTrue(response.getResponses().stream()
                .allMatch(item -> item.responseData().toString().contains("ReAct")));
    }

    @Test
    void executionStageShouldForceWritePlanBeforeOtherPlanExecutorTools() {
        context.setExecutionMode(ModeEnum.PLAN_EXECUTOR);
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall("1", "function", "meeting_tool", "{}"));

        ToolResponseMessage response = toolInterceptor.interceptIllegalToolCalls(context, toolCalls);

        assertNotNull(response);
        assertEquals(1, response.getResponses().size());
        assertTrue(response.getResponses().getFirst().responseData().toString().contains("write_plan_tool"));
    }

    @Test
    void executionStageShouldAllowPlanExecutorBusinessToolsAfterPlanStartsRunning() {
        context.setExecutionMode(ModeEnum.PLAN_EXECUTOR);
        context.setPlan(runningPlan());
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall("1", "function", "meeting_tool", "{}"));

        ToolResponseMessage response = toolInterceptor.interceptIllegalToolCalls(context, toolCalls);

        assertNull(response);
    }

    private Plan runningPlan() {
        Stage stage = new Stage();
        stage.setId("stage-1");
        stage.setStatus(TaskStatus.RUNNING);

        Plan plan = new Plan();
        plan.setStages(new ArrayList<>(List.of(stage)));
        return plan;
    }
}
