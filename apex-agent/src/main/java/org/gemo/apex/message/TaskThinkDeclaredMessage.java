package org.gemo.apex.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * TASK_THINK_DECLARED - 思考标记声明消息
 *
 * @author gemo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class TaskThinkDeclaredMessage extends AgentMessage {

    /**
     * 思考声明列表
     */
    @JsonProperty("messages")
    protected List<TaskThinkDeclaredDetail> messages;

    @lombok.Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TaskThinkDeclaredDetail {
        /**
         * 思考标记ID
         */
        @JsonProperty("task_id")
        private String taskId;

        /**
         * 初始内容
         */
        @JsonProperty("content")
        private String content;
    }
}
