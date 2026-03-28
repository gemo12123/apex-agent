package org.gemo.apex.tool;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.context.SuperAgentContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 模式确认阶段：写入后续运转模式工具。
 * 通过 ToolContext 直接将确认的执行模式写入 SuperAgentContext，并将阶段流转至 EXECUTION。
 */
@Slf4j
public class WriteModeTool {

    @Tool(name = ToolNames.WRITE_MODE, description = "在确定执行模式阶段必须且只允许调用的工具。用于决定下一步是进入需要详细步骤的 PlanExecutor 还是简单的 ReAct 模式。")
    public String writeMode(
            @ToolParam(description = "确定的执行模式，枚举值：'PlanExecutor' (适合复杂规划任务) 或 'ReAct' (适合单步调用或简单问答)") String mode,
            ToolContext toolContext) {

        SuperAgentContext context = (SuperAgentContext) toolContext.getContext().get(ToolContextKeys.SESSION_CONTEXT);
        if (context != null) {
            // 将 LLM 传入的自由文本（如 "PlanExecutor"）标准化为枚举值
            ModeEnum modeEnum = ModeEnum.fromLlmValue(mode);
            context.setExecutionMode(modeEnum);
            context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
            log.info("write_mode 已写入执行模式: {}（标准化: {}），阶段流转至 EXECUTION", mode, modeEnum.name());
        } else {
            log.warn("write_mode 未获取到 SuperAgentContext，模式写入失败");
        }

        return "MODE_CONFIRMED: " + mode;
    }
}
