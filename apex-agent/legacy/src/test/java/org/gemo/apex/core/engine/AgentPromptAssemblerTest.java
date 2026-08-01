package org.gemo.apex.core.engine;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentPromptAssemblerTest {

    @Test
    void assembleShouldExposeSerializableMcpSessionContextAlongsideRuntimeContext() {
        ConversationMemoryManager conversationMemoryManager = mock(ConversationMemoryManager.class);
        StagePromptBuilder stagePromptBuilder = mock(StagePromptBuilder.class);
        AgentPromptAssembler assembler = new AgentPromptAssembler(conversationMemoryManager, stagePromptBuilder);

        SuperAgentContext context = new SuperAgentContext();
        context.setSessionId("session-1");
        context.setAgentKey("agent-1");
        context.setUserId("user-1");
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        context.setCurrentStageId("stage-1");
        context.setExecutionMode(ModeEnum.REACT);
        context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);
        context.setTurnNo(3);

        when(stagePromptBuilder.build(context, List.of())).thenReturn("system prompt");
        when(conversationMemoryManager.buildModelMessages(context)).thenReturn(List.of(new UserMessage("hello")));

        Prompt prompt = assembler.assemble(context, new StageToolPlan(List.of(), List.of()));

        verify(conversationMemoryManager).refreshFixedMessages(context, "system prompt");
        verify(conversationMemoryManager).compactIfNeeded(context);

        DashScopeChatOptions options = (DashScopeChatOptions) prompt.getOptions();
        Map<String, Object> toolContext = options.getToolContext();

        assertSame(context, toolContext.get(ToolContextKeys.SESSION_CONTEXT));
        assertTrue(toolContext.containsKey("MCP_SESSION_CONTEXT"));

        Object snapshotObject = toolContext.get("MCP_SESSION_CONTEXT");
        assertInstanceOf(Map.class, snapshotObject);

        Map<?, ?> snapshot = (Map<?, ?>) snapshotObject;
        assertEquals("session-1", snapshot.get("sessionId"));
        assertEquals("agent-1", snapshot.get("agentKey"));
        assertEquals("user-1", snapshot.get("userId"));
        assertEquals("EXECUTION", snapshot.get("currentStage"));
        assertEquals("stage-1", snapshot.get("currentStageId"));
        assertEquals("react", snapshot.get("executionMode"));
        assertEquals("IN_PROGRESS", snapshot.get("executionStatus"));
        assertEquals(3L, snapshot.get("turnNo"));
        assertFalse(snapshot.containsKey("lastActiveTime"));
    }

    @Test
    void assembleShouldUseEnabledToolsAfterHookAdjustment() {
        ConversationMemoryManager conversationMemoryManager = mock(ConversationMemoryManager.class);
        StagePromptBuilder stagePromptBuilder = mock(StagePromptBuilder.class);
        AgentPromptAssembler assembler = new AgentPromptAssembler(conversationMemoryManager, stagePromptBuilder);
        ToolCallback configuredTool = mock(ToolCallback.class);
        when(configuredTool.getToolDefinition()).thenReturn(DefaultToolDefinition.builder()
                .name("meeting_tool")
                .description("meeting")
                .inputSchema("{}")
                .build());
        SuperAgentContext context = new SuperAgentContext();
        StageToolPlan plan = new StageToolPlan(List.of(configuredTool), List.of(configuredTool));

        Prompt prompt = assembler.assemble(
                context,
                plan,
                List.of(new UserMessage("hello")),
                List.of());

        DashScopeChatOptions options = (DashScopeChatOptions) prompt.getOptions();
        assertEquals(List.of(), options.getToolCallbacks());
    }
}
