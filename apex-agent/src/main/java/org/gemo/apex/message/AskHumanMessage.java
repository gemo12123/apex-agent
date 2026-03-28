package org.gemo.apex.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * ASK_HUMAN - 请求用户输入消息
 * 当智能体需要用户补充信息时发送此消息
 *
 * @author gemo
 * @date 2026/02/10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class AskHumanMessage extends AgentMessage {

    /**
     * 消息内容列表
     */
    @JsonProperty("messages")
    private List<AskHumanDetail> messages;

    /**
     * AskHuman 详情
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class AskHumanDetail {
        /**
         * 交互类型：TEXT_INPUT, SINGLE_SELECT, CONFIRM, MULTI_SELECT
         */
        @JsonProperty("input_type")
        private String inputType;

        /**
         * 向用户提出的问题
         */
        @JsonProperty("question")
        private String question;

        /**
         * 问题描述（可选，提供更多上下文）
         */
        @JsonProperty("description")
        private String description;

        /**
         * 选项列表（SELECT/MULTI_SELECT 类型使用）
         */
        @JsonProperty("options")
        private List<Option> options;

        /**
         * 工具调用 ID（前端回复时可能需要，虽然不一定要回传）
         */
        @JsonProperty("tool_call_id")
        private String toolCallId;

    }

    /**
     * 选项定义
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class Option {

        /**
         * 选项显示文本
         */
        @JsonProperty("label")
        private String label;

        /**
         * 选项描述（可选）
         */
        @JsonProperty("description")
        private String description;
    }
}
