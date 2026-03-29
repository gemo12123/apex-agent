package org.gemo.apex.core.engine;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.Map;

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
        String stageSystemPrompt = stagePromptBuilder.build(context, toolPlan.promptDescribedTools());
        conversationMemoryManager.refreshFixedMessages(context, stageSystemPrompt);
        conversationMemoryManager.compactIfNeeded(context);

        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .withToolCallbacks(toolPlan.callableTools())
                .withToolContext(Map.of(ToolContextKeys.SESSION_CONTEXT, context))
                .build();

        return new Prompt(conversationMemoryManager.buildModelMessages(context), options);
    }
}
