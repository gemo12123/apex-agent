package org.gemo.apex.component.interceptor;

import org.gemo.apex.constant.Constant;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.TaskStatus;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.domain.Stage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SuperAgent 工具拦截器
 * 用于在各个执行阶段拦截不属于该阶段允许范围的工具调用请求，阻止其被真实执行，
 * 并向大模型返回特定的错误提示，以纠正大模型的行径。
 */
@Component
public class ToolInterceptor {

    /**
     * 校验大模型给出的 ToolCall 列表是否在当前上下文阶段允许之内。
     *
     * @param context   当前的会话上下文及所处阶段
     * @param toolCalls LLM 给出的意图调用列表
     * @return 包含非法工具调用的 ToolResponseMessage（将被丢回给大模型纠正）。如果全部合法或无需拦截，则返回 null。
     */
    public ToolResponseMessage interceptIllegalToolCalls(SuperAgentContext context,
            List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }

        List<ToolResponseMessage.ToolResponse> errorResponses = new ArrayList<>();

        for (AssistantMessage.ToolCall call : toolCalls) {
            String toolName = call.name();
            String errorMsg = checkToolLegality(context, toolName);

            if (errorMsg != null) {
                // 生成针对该非法语义调用的错误响应包
                errorResponses.add(new ToolResponseMessage.ToolResponse(call.id(), toolName, errorMsg));
            }
        }

        if (!errorResponses.isEmpty()) {
            return ToolResponseMessage.builder().responses(errorResponses).build();
        }

        return null;
    }

    /**
     * 针对不同阶段进行权限校验
     *
     * @param context  当前上下文状态
     * @param toolName 工具名
     * @return 返回错误提示语（如果不合法）；合法则返回 null
     */
    private String checkToolLegality(SuperAgentContext context, String toolName) {
        SuperAgentContext.Stage stage = context.getCurrentStage();

        switch (stage) {
            case THINKING:
                // 思考阶段只允许 active_skills, direct_answer
                if (!toolName.equals(Constant.SKILLS_TOOL_NAME) && !toolName.equals(ToolNames.DIRECT_ANSWER)) {
                    return "当前处于思考(Thinking)阶段，不允许执行 [" + toolName
                            + "]。请仅使用 `activate_skill` 引入技能或 `direct_answer` 直接结束作答，若思考完成且无需作答，请直接下发不含任何工具调用的最终文本，以进入模式确认阶段。";
                }
                break;

            case MODE_CONFIRMATION:
                // 确认执行分支阶段只允许 write_mode
                if (!toolName.equals(ToolNames.WRITE_MODE)) {
                    return "当前处于模式枚举确认阶段，强烈要求只能调用 `write_mode` 工具，传入模式必须为 'PlanExecutor' 或是 'ReAct'。";
                }
                break;

            case EXECUTION:
                // 在 Execution 阶段根据实际执行模式不同做拦截
                if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR) {
                    if (context.getPlan() == null) {
                        // PlanExecutor 初期强制调用 write_plan_tool，尚未写过计划时禁止调用其他工具
                        if (!toolName.equals(Constant.PLAN_TOOL_NAME)) {
                            return "当前处于 PlanExecutor 执行模式的初期，强制要求首先调用 `" + Constant.PLAN_TOOL_NAME + "` 工具提交拆解执行计划。";
                        }
                    } else {
                        // PlanExecutor 已初始化：拦截 direct_answer, write_mode, write_plan_tool
                        if (toolName.equals(ToolNames.DIRECT_ANSWER) || toolName.equals(ToolNames.WRITE_MODE)
                                || toolName.equals(Constant.PLAN_TOOL_NAME)) {
                            return "当前处于 PlanExecutor 执行阶段，不允许调用 [" + toolName + "]。";
                        }

                        // 判断是否有正在运行的阶段
                        boolean hasRunningStage = false;
                        if (context.getPlan() != null && context.getPlan().getStages() != null) {
                            for (Stage s : context.getPlan().getStages()) {
                                if (s.getStatus() == TaskStatus.RUNNING) {
                                    hasRunningStage = true;
                                    break;
                                }
                            }
                        }

                        // 如果没有正在运行的阶段，且当前调用的不是 update_plan_tool，则拒绝执行并要求其开始阶段
                        if (!hasRunningStage && !toolName.equals(Constant.REPLAN_TOOL_NAME)) {
                            return "当前 PlanExecutor 计划中没有正在运行(RUNNING)的阶段。在执行工具 [" + toolName + "] 之前，请先使用 `"
                                    + Constant.REPLAN_TOOL_NAME + "` 工具（参数 operation 设为 'START_CURRENT'）来开始一个阶段。";
                        }
                    }
                } else if (context.getExecutionMode() == ModeEnum.REACT) {
                    // ReAct 模式：拦截 direct_answer, write_mode, write_plan_tool, update_plan_tool
                    if (toolName.equals(ToolNames.DIRECT_ANSWER) || toolName.equals(ToolNames.WRITE_MODE)
                            || toolName.equals(Constant.PLAN_TOOL_NAME)
                            || toolName.equals(Constant.REPLAN_TOOL_NAME)) {
                        return "当前处于 ReAct 执行阶段，不允许调用 [" + toolName + "]。";
                    }
                }
                break;

            default:
                break;
        }

        return null; // 合法
    }

    /**
     * 检查上下文历史消息中是否已经发生过 write_plan 调用。
     * <p>
     * 同时检查 AssistantMessage 中的 ToolCall（模型发起调用）
     * 以及 ToolResponseMessage 中的 ToolResponse（工具执行结果）两处。
     */
    private boolean hasWrittenPlan(SuperAgentContext context) {
        for (org.springframework.ai.chat.messages.Message msg : context.getMessages()) {
            if (msg instanceof AssistantMessage) {
                AssistantMessage am = (AssistantMessage) msg;
                if (am.getToolCalls() != null) {
                    for (AssistantMessage.ToolCall call : am.getToolCalls()) {
                        if (Constant.PLAN_TOOL_NAME.equals(call.name())) {
                            return true;
                        }
                    }
                }
            } else if (msg instanceof ToolResponseMessage) {
                ToolResponseMessage trm = (ToolResponseMessage) msg;
                if (trm.getResponses() != null) {
                    for (ToolResponseMessage.ToolResponse resp : trm.getResponses()) {
                        if (Constant.PLAN_TOOL_NAME.equals(resp.name())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
