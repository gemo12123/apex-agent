package org.gemo.apex.definition.agent;

import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.constant.ModeEnum;

import java.util.List;

public record AgentDefinition(
        String agentKey,
        ModeEnum defaultExecutionMode,
        List<String> mcpNames,
        List<String> subAgentNames,
        List<String> skillNames,
        AgentHooksConfig hooks,
        String reactPrompt,
        String planExecutorWritePlanPrompt,
        String planExecutorRunPrompt,
        String agentRules) {
}
