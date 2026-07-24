package org.gemo.apex.core.engine;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.Message;

@Component
public class AgentPromptAssembler {

    private final ConversationMemoryManager conversationMemoryManager;
    private final StagePromptBuilder stagePromptBuilder;

    public AgentPromptAssembler(ConversationMemoryManager conversationMemoryManager,
            StagePromptBuilder stagePromptBuilder) {
        this.conversationMemoryManager = conversationMemoryManager;
        this.stagePromptBuilder = stagePromptBuilder;
    }

    public Prompt assemble(SuperAgentContext context, StageToolPlan toolPlan) {
        return assemble(context, toolPlan, prepareWorkingMessages(context, toolPlan));
    }

    public List<Message> prepareWorkingMessages(SuperAgentContext context, StageToolPlan toolPlan) {
        String stageSystemPrompt = stagePromptBuilder.build(context, toolPlan.promptDescribedTools());
        conversationMemoryManager.refreshFixedMessages(context, stageSystemPrompt);
        conversationMemoryManager.compactIfNeeded(context);
        return new java.util.ArrayList<>(conversationMemoryManager.buildModelMessages(context));
    }

    public Prompt assemble(SuperAgentContext context, StageToolPlan toolPlan, List<Message> workingMessages) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .withToolCallbacks(toolPlan.callableTools())
                .withToolContext(buildToolContext(context, Map.of()))
                .withMultiModel(true)
                .build();

        return new Prompt(workingMessages != null ? new java.util.ArrayList<>(workingMessages) : List.of(), options);
    }

    public Prompt assembleToolExecutionPrompt(SuperAgentContext context, Map<String, Object> extraToolContext) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .withToolCallbacks(context.getAvailableTools())
                .withToolContext(buildToolContext(context, extraToolContext != null ? extraToolContext : Map.of()))
                .build();

        List<Message> messages = context.getWorkingMessages() != null && !context.getWorkingMessages().isEmpty()
                ? context.getWorkingMessages()
                : conversationMemoryManager.buildModelMessages(context);
        return new Prompt(new java.util.ArrayList<>(messages), options);
    }

    private Map<String, Object> buildToolContext(SuperAgentContext context, Map<String, Object> extraToolContext) {
        Map<String, Object> toolContext = new LinkedHashMap<>();
        toolContext.put(ToolContextKeys.SESSION_CONTEXT, context);
        toolContext.put(ToolContextKeys.MCP_SESSION_CONTEXT, buildMcpSessionContext(context));
        toolContext.putAll(extraToolContext);
        return Map.copyOf(toolContext);
    }

    private Map<String, Object> buildMcpSessionContext(SuperAgentContext context) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putIfNotNull(snapshot, "sessionId", context.getSessionId());
        putIfNotNull(snapshot, "agentKey", context.getAgentKey());
        putIfNotNull(snapshot, "userId", context.getUserId());
        if (context.getCurrentStage() != null) {
            snapshot.put("currentStage", context.getCurrentStage().name());
        }
        putIfNotNull(snapshot, "currentStageId", context.getCurrentStageId());
        if (context.getExecutionMode() != null) {
            snapshot.put("executionMode", context.getExecutionMode().getMode());
        }
        if (context.getExecutionStatus() != null) {
            snapshot.put("executionStatus", context.getExecutionStatus().name());
        }
        putIfNotNull(snapshot, "turnNo", context.getTurnNo());
        return Map.copyOf(snapshot);
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
