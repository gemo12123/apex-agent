package org.gemo.apex.hook.lifecycle;

import lombok.Builder;
import lombok.Data;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.definition.agent.AgentDefinition;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AgentRuntimeContext {
    private AgentExecutionStore executionStore;
    private SuperAgentContext sessionContext;
    private AgentDefinition agentDefinition;
    private AgentTurn turn;
    private AgentTrace trace;
    @Builder.Default
    private List<Message> workingMessages = new ArrayList<>();
    @Builder.Default
    private List<ToolCallback> availableTools = new ArrayList<>();
    @Builder.Default
    private List<ToolCallback> enabledTools = new ArrayList<>();
    @Builder.Default
    private List<String> activeSkillNames = new ArrayList<>();
    @Builder.Default
    private List<ToolCallRecord> turnToolCalls = new ArrayList<>();
    private ChatResponse originalModelOutput;
    private AssistantMessage finalModelOutput;
    private AssistantMessage.ToolCall currentToolCall;
    @Builder.Default
    private Map<String, Object> currentToolArguments = new LinkedHashMap<>();
    private String currentToolOriginalResult;
    private String currentToolResult;
}
