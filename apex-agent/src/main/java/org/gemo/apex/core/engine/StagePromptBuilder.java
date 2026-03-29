package org.gemo.apex.core.engine;

import org.apache.commons.lang3.StringUtils;
import org.gemo.apex.constant.Constant;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.service.AgentWorkspaceService;
import org.gemo.apex.tool.skills.CustomSkillsTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class StagePromptBuilder {

    private final AgentWorkspaceService agentWorkspaceService;

    public StagePromptBuilder(AgentWorkspaceService agentWorkspaceService) {
        this.agentWorkspaceService = agentWorkspaceService;
    }

    public String build(SuperAgentContext context, List<ToolCallback> promptDescribedTools) {
        String agentKey = context.getAgentKey();

        String skillsXml = "";
        CustomSkillsTool skillsTool = context.getCustomSkillsTool();
        if (skillsTool != null && skillsTool.getSkillsXml() != null) {
            skillsXml = skillsTool.getSkillsXml();
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
                    ? agentWorkspaceService.getPlanExecutorWritePlanPrompt(agentKey)
                    : agentWorkspaceService.getPlanExecutorRunPrompt(agentKey);
        } else {
            stagePromptTemplate = agentWorkspaceService.getReActPrompt(agentKey);
        }

        if (stagePromptTemplate == null) {
            stagePromptTemplate = "";
        }

        String parsedPrompt = stagePromptTemplate
                .replace("{skills}", skillsXml)
                .replace("{available_tools_desc}", toolsDesc.toString())
                .replace("{date}", Constant.DATE_TIME_FORMATTER.format(LocalDateTime.now()));

        String agentRules = agentWorkspaceService.getAgentRules(agentKey);
        if (StringUtils.isNotEmpty(agentRules)) {
            parsedPrompt = parsedPrompt + "\n\n=== Global Execution Rules ===\n" + agentRules;
        }
        return parsedPrompt;
    }
}
