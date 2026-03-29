package org.gemo.apex.core.engine;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;

public interface AgentToolExecutor {

    ToolResponseMessage execute(Prompt prompt, AssistantMessage assistantMessage);
}
