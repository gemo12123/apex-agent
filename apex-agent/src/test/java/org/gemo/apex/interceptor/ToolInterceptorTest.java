package org.gemo.apex.interceptor;

import org.gemo.apex.component.interceptor.ToolInterceptor;
import org.gemo.apex.context.SuperAgentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ToolInterceptorTest {

    private ToolInterceptor toolInterceptor;
    private SuperAgentContext context;

    @BeforeEach
    public void setup() {
        toolInterceptor = new ToolInterceptor();
        context = new SuperAgentContext();
    }

    @Test
    public void testThinkingStage_AllowsActiveSkills() {
        context.setCurrentStage(SuperAgentContext.Stage.THINKING);
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall("1", "function", "activate_skill", "{}"));

        ToolResponseMessage response = toolInterceptor.interceptIllegalToolCalls(context, toolCalls);
        assertNull(response, "THINKING 阶段应该允许 activate_skill，返回 null");
    }

    @Test
    public void testThinkingStage_AllowsDirectAnswer() {
        context.setCurrentStage(SuperAgentContext.Stage.THINKING);
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall("2", "function", "direct_answer", "{}"));

        ToolResponseMessage response = toolInterceptor.interceptIllegalToolCalls(context, toolCalls);
        assertNull(response, "THINKING 阶段应该允许 direct_answer，返回 null");
    }

    @Test
    public void testThinkingStage_RejectsOtherTools() {
        context.setCurrentStage(SuperAgentContext.Stage.THINKING);
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall("3", "function", "write_mode", "{}"));

        ToolResponseMessage response = toolInterceptor.interceptIllegalToolCalls(context, toolCalls);
        assertNotNull(response, "THINKING 阶段调用 write_mode 应该被拦截");
        assertEquals(1, response.getResponses().size());
        assertTrue(response.getResponses().get(0).responseData().toString().contains("不允许执行 [write_mode]"));
    }

    @Test
    public void testModeConfirmationStage_AllowsWriteMode() {
        context.setCurrentStage(SuperAgentContext.Stage.MODE_CONFIRMATION);
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall("4", "function", "write_mode", "{}"));

        ToolResponseMessage response = toolInterceptor.interceptIllegalToolCalls(context, toolCalls);
        assertNull(response, "MODE_CONFIRMATION 阶段应该允许 write_mode");
    }

    @Test
    public void testModeConfirmationStage_RejectsOtherTools() {
        context.setCurrentStage(SuperAgentContext.Stage.MODE_CONFIRMATION);
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall("5", "function", "activate_skill", "{}"));

        ToolResponseMessage response = toolInterceptor.interceptIllegalToolCalls(context, toolCalls);
        assertNotNull(response, "MODE_CONFIRMATION 阶段不应该允许 activate_skill");
        assertEquals(1, response.getResponses().size());
        assertTrue(response.getResponses().get(0).responseData().toString().contains("只能调用 `write_mode` 工具"));
    }
}
