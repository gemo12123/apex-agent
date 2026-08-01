package org.gemo.apex.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * TASK_THINK_CHANGE - 思考标记变更消息
 *
 * @author gemo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class TaskThinkChangeMessage extends AgentMessage {

    /**
     * 思考变更信息列表
     */
    @JsonProperty("messages")
    protected List<TaskThinkChangeDetail> messages;

    @lombok.Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TaskThinkChangeDetail {
        /**
         * 变更类型：STATUS_CHANGE, CONTENT_APPEND
         */
        @JsonProperty("change_type")
        private String changeType;

        /**
         * 思考标记ID
         */
        @JsonProperty("task_id")
        private String taskId;

        /**
         * 状态（STATUS_CHANGE时使用，如 COMPLETED, FAILED, RUNNING）
         */
        @JsonProperty("status")
        private String status;

        /**
         * 增量内容（CONTENT_APPEND时使用）
         */
        @JsonProperty("content")
        private String content;
    }
}
