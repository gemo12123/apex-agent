package org.gemo.apex.core.engine;

import org.gemo.apex.constant.Constant;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.context.SuperAgentContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class StageToolResolver {

    public StageToolPlan resolve(SuperAgentContext context) {
        List<ToolCallback> callableTools = new ArrayList<>();
        List<ToolCallback> promptDescribedTools = new ArrayList<>();
        Set<String> builtInToolNames = Set.of(
                ToolNames.ASK_HUMAN, ToolNames.DIRECT_ANSWER, ToolNames.WRITE_MODE,
                Constant.PLAN_TOOL_NAME, Constant.REPLAN_TOOL_NAME, Constant.SKILLS_TOOL_NAME);

        for (ToolCallback tool : context.getAvailableTools()) {
            String toolName = tool.getToolDefinition().name();
            boolean isMcpOrSubAgent = !builtInToolNames.contains(toolName);

            if (context.getCurrentStage() == SuperAgentContext.Stage.THINKING) {
                if (Constant.SKILLS_TOOL_NAME.equals(toolName) || ToolNames.DIRECT_ANSWER.equals(toolName)) {
                    callableTools.add(tool);
                } else if (ToolNames.ASK_HUMAN.equals(toolName) || isMcpOrSubAgent) {
                    promptDescribedTools.add(tool);
                }
            } else if (context.getCurrentStage() == SuperAgentContext.Stage.MODE_CONFIRMATION) {
                if (ToolNames.WRITE_MODE.equals(toolName)) {
                    callableTools.add(tool);
                } else if (ToolNames.ASK_HUMAN.equals(toolName) || Constant.SKILLS_TOOL_NAME.equals(toolName)
                        || isMcpOrSubAgent) {
                    promptDescribedTools.add(tool);
                }
            } else if (context.getCurrentStage() == SuperAgentContext.Stage.EXECUTION) {
                if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR && context.getPlan() == null) {
                    if (Constant.PLAN_TOOL_NAME.equals(toolName)) {
                        callableTools.add(tool);
                    } else if (ToolNames.ASK_HUMAN.equals(toolName) || Constant.SKILLS_TOOL_NAME.equals(toolName)
                            || Constant.REPLAN_TOOL_NAME.equals(toolName) || isMcpOrSubAgent) {
                        promptDescribedTools.add(tool);
                    }
                } else {
                    if (ToolNames.DIRECT_ANSWER.equals(toolName)
                            || ToolNames.WRITE_MODE.equals(toolName)
                            || Constant.PLAN_TOOL_NAME.equals(toolName)) {
                        continue;
                    }
                    if (Constant.REPLAN_TOOL_NAME.equals(toolName)) {
                        if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR) {
                            callableTools.add(tool);
                        }
                    } else {
                        callableTools.add(tool);
                    }
                }
            }
        }

        return new StageToolPlan(List.copyOf(callableTools), List.copyOf(promptDescribedTools));
    }
}
