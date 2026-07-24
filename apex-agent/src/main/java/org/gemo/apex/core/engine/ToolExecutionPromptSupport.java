package org.gemo.apex.core.engine;

import org.gemo.apex.constant.ToolContextKeys;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.LinkedHashMap;
import java.util.Map;

final class ToolExecutionPromptSupport {

    private ToolExecutionPromptSupport() {
    }

    static PreparedToolExecutionPrompt prepare(Prompt prompt, String invocationId) {
        ToolExecutionOutcome outcome = new ToolExecutionOutcome();
        if (prompt == null || invocationId == null || prompt.getOptions() == null) {
            return new PreparedToolExecutionPrompt(prompt, outcome);
        }
        ChatOptions copiedOptions = prompt.getOptions().copy();
        if (!(copiedOptions instanceof ToolCallingChatOptions toolOptions)) {
            return new PreparedToolExecutionPrompt(prompt, outcome);
        }
        Map<String, Object> toolContext = new LinkedHashMap<>();
        if (toolOptions.getToolContext() != null) {
            toolContext.putAll(toolOptions.getToolContext());
        }
        toolContext.put(ToolContextKeys.INVOCATION_ID, invocationId);
        toolContext.put(ToolContextKeys.EXECUTION_OUTCOME, outcome);
        toolOptions.setToolContext(Map.copyOf(toolContext));
        return new PreparedToolExecutionPrompt(new Prompt(prompt.getInstructions(), copiedOptions), outcome);
    }

    record PreparedToolExecutionPrompt(Prompt prompt, ToolExecutionOutcome outcome) {
    }
}
