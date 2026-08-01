package org.gemo.apex.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * INVOCATION_DECLARED - 执行标记声明消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class InvocationDeclaredMessage extends AgentMessage {

    /**
     * 执行标记信息列表
     */
    @JsonProperty("messages")
    protected List<InvocationMessage> messages;

    @lombok.Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class InvocationMessage {
        /**
         * 执行标记ID
         */
        @JsonProperty("invocation_id")
        private String invocationId;

        /**
         * 执行标记名称
         */
        @JsonProperty("name")
        private String name;

        /**
         * 执行标记类型：search, think等，主要用于图标显示
         */
        @JsonProperty("invocation_type")
        private String invocationType;

        /**
         * 点击效果
         */
        @JsonProperty("click_effect")
        private String clickEffect;

        /**
         * 内容，可为空，用于初始化定义
         */
        @JsonProperty("content")
        private String content;

        /**
         * 该阶段是否直接结束，默认false
         */
        @JsonProperty("complete")
        private boolean complete;

        @JsonProperty("render_type")
        private String renderType;
    }
}
