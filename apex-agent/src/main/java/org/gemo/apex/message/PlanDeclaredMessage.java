package org.gemo.apex.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * PLAN_DECLARED - 计划声明消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class PlanDeclaredMessage extends AgentMessage {

    /**
     * 阶段信息列表
     */
    @JsonProperty("messages")
    protected List<StageMessage> messages;

    @lombok.Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StageMessage {
        /**
         * 阶段ID
         */
        @JsonProperty("stage_id")
        private String stageId;

        /**
         * 阶段名称
         */
        @JsonProperty("stage_name")
        private String stageName;

        /**
         * 阶段描述
         */
        @JsonProperty("description")
        private String description;

        /**
         * 状态：PENDING, RUNNING, COMPLETED, FAILED
         */
        @JsonProperty("status")
        private String status;
    }
}
