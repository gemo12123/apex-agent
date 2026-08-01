package org.gemo.apex.core.engine;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringAiAgentToolExecutor implements AgentToolExecutor {

    private final ToolCallingManager toolCallingManager;

    public SpringAiAgentToolExecutor(ToolCallingManager toolCallingManager) {
        this.toolCallingManager = toolCallingManager;
    }

    @Override
    public ToolResponseMessage execute(Prompt prompt, AssistantMessage assistantMessage) {
        ChatResponse toolCallResponse = new ChatResponse(List.of(new Generation(assistantMessage)));
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallResponse);
        return toolExecutionResult.conversationHistory().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "ToolCallingManager did not return ToolResponseMessage for assistant tool calls"));
    }
}
