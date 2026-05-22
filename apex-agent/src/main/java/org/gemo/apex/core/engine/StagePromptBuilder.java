package org.gemo.apex.core.engine;

import org.apache.commons.lang3.StringUtils;
import org.gemo.apex.constant.Constant;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.definition.agent.AgentDefinition;
import org.gemo.apex.definition.agent.IAgentDefinitionLoader;
import org.gemo.apex.skills.Skills;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class StagePromptBuilder {

    private final IAgentDefinitionLoader agentDefinitionLoader;

    public StagePromptBuilder(IAgentDefinitionLoader agentDefinitionLoader) {
        this.agentDefinitionLoader = agentDefinitionLoader;
    }

    public String build(SuperAgentContext context, List<ToolCallback> promptDescribedTools) {
        String agentKey = context.getAgentKey();
        AgentDefinition definition = agentDefinitionLoader.load(agentKey);

        String skillsXml = "";
        Skills skills = context.getSkills();
        if (skills != null) {
            skillsXml = skills.formatAvailableSkills();
        }

        StringBuilder toolsDesc = new StringBuilder();
        if (!promptDescribedTools.isEmpty()) {
            for (ToolCallback tool : promptDescribedTools) {
                toolsDesc.append("- ").append(tool.getToolDefinition().name())
                        .append(": ").append(tool.getToolDefinition().description()).append("\n");
            }
        } else {
            toolsDesc.append("无\n");
        }

        String stagePromptTemplate = "";
        if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR) {
            stagePromptTemplate = context.getPlan() == null
                    ? definition.planExecutorWritePlanPrompt()
                    : definition.planExecutorRunPrompt();
        } else {
            stagePromptTemplate = definition.reactPrompt();
        }

        if (stagePromptTemplate == null) {
            stagePromptTemplate = "";
        }

        String parsedPrompt = stagePromptTemplate
                .replace("{skills}", skillsXml)
                .replace("{available_tools_desc}", toolsDesc.toString())
                .replace("{date}", Constant.DATE_TIME_FORMATTER.format(LocalDateTime.now()));

        String agentRules = definition.agentRules();
        if (StringUtils.isNotEmpty(agentRules)) {
            parsedPrompt = parsedPrompt + "\n\n=== Global Execution Rules ===\n" + agentRules;
        }
        return parsedPrompt;
    }
}
