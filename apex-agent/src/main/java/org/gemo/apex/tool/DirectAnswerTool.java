package org.gemo.apex.tool;

import org.gemo.apex.constant.ToolNames;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 思考阶段：直接作答工具
 */
public class DirectAnswerTool {

    @Tool(name = ToolNames.DIRECT_ANSWER, description = "在思考阶段，如果判定可以直接解答用户问题而无需进入后续复杂规划或调用额外工具时使用。使用此工具后将直接输出结果给用户并结束本轮思考。")
    public String direct_answer(
            @ToolParam(description = "直接回复给用户的最终答案") String answer) {

        return answer;
    }
}
