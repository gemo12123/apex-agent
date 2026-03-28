package org.gemo.apex.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * INVOCATION_CHANGE - 执行标记变更消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class InvocationChangeMessage extends AgentMessage {

    /**
     * 变更信息列表
     */
    @JsonProperty("messages")
    protected List<InvocationChangeDetail> messages;

    @lombok.Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class InvocationChangeDetail {
        /**
         * 变更类型：STATUS_CHANGE, CONTENT_APPEND
         */
        @JsonProperty("change_type")
        private String changeType;

        /**
         * 执行标记ID
         */
        @JsonProperty("invocation_id")
        private String invocationId;

        /**
         * 状态（STATUS_CHANGE时使用）
         */
        @JsonProperty("status")
        private String status;

        /**
         * 内容（CONTENT_APPEND时使用）
         */
        @JsonProperty("content")
        private String content;

        @JsonProperty("render_type")
        private String renderType;
    }
}
