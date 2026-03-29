package org.gemo.apex.component.interceptor;

import org.gemo.apex.constant.Constant;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.TaskStatus;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.domain.Stage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ToolInterceptor {

    public ToolResponseMessage interceptIllegalToolCalls(SuperAgentContext context,
            List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }

        List<ToolResponseMessage.ToolResponse> errorResponses = new ArrayList<>();
        for (AssistantMessage.ToolCall call : toolCalls) {
            String errorMsg = checkToolLegality(context, call.name());
            if (errorMsg != null) {
                errorResponses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), errorMsg));
            }
        }

        if (errorResponses.isEmpty()) {
            return null;
        }
        return ToolResponseMessage.builder().responses(errorResponses).build();
    }

    private String checkToolLegality(SuperAgentContext context, String toolName) {
        if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR) {
            if (context.getPlan() == null) {
                if (!toolName.equals(Constant.PLAN_TOOL_NAME)) {
                    return "当前处于 PlanExecutor 执行模式的初始阶段，必须先调用 `" + Constant.PLAN_TOOL_NAME
                            + "` 提交执行计划。";
                }
                return null;
            }

            if (toolName.equals(Constant.PLAN_TOOL_NAME)) {
                return "当前处于 PlanExecutor 执行阶段，不允许调用 [" + toolName + "]。";
            }

            boolean hasRunningStage = false;
            if (context.getPlan().getStages() != null) {
                for (Stage stage : context.getPlan().getStages()) {
                    if (stage.getStatus() == TaskStatus.RUNNING) {
                        hasRunningStage = true;
                        break;
                    }
                }
            }

            if (!hasRunningStage && !toolName.equals(Constant.REPLAN_TOOL_NAME)) {
                return "当前 PlanExecutor 计划中没有 RUNNING 阶段。请先调用 `" + Constant.REPLAN_TOOL_NAME
                        + "` 开始当前阶段。";
            }
            return null;
        }

        if (context.getExecutionMode() == ModeEnum.REACT) {
            if (toolName.equals(Constant.PLAN_TOOL_NAME) || toolName.equals(Constant.REPLAN_TOOL_NAME)) {
                return "当前处于 ReAct 执行阶段，不允许调用 [" + toolName + "]。";
            }
        }

        return null;
    }
}
