package org.gemo.apex.tool;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.message.AskHumanMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;

/**
 * Ask Human Tool (人在回路)
 * 当任务信息不全或需要用户确认关键步骤时，大模型应该调用此工具向用户提问。
 * 此工具将会中断当前的循环并下发请求给前端，等待前端返回数据后恢复上下文执行。
 */
@Slf4j
public class AskHumanTool {

    @Tool(name = ToolNames.ASK_HUMAN, description = "当遇到信息不全或必须要求用户确认才能继续的操作时，请使用此工具挂起任务向用户提问。系统会中断运行并将问题推送给用户终端，直至用户提供反馈后恢复运行。")
    public String execute(List<Request> request, ToolContext toolContext) {

        // 挂起信号处理通常在 Engine 拦截器中更早触发或者当这个工具被调用返回标志位后由大循环读取
        // 在新版 SuperAgent 中，该工具一旦被模型请求，系统拦截并置
        // context.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
        return "ASK_HUMAN_SUSPENDED";
    }

    @JsonClassDescription("向用户发起的交互提问请求体")
    public record Request(
            @JsonProperty(required = true, value = "question") @JsonPropertyDescription("向用户提出的明确问题，用于展示在界面上") String question,

            @JsonProperty(required = false, value = "interaction_type") @JsonPropertyDescription("期望用户进行的输入类型：TEXT_INPUT(文本回答), SINGLE_SELECT(单选), CONFIRM(按钮确认), MULTI_SELECT(多选)") String interactionType,

            @JsonProperty(required = false, value = "options") @JsonPropertyDescription("备选的列表（针对 SINGLE_SELECT 或 MULTI_SELECT 时使用）") List<AskHumanMessage.Option> options) {
    }
}
