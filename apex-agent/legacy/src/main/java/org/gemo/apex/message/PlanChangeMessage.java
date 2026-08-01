package org.gemo.apex.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * PLAN_CHANGE - 计划变更消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class PlanChangeMessage extends AgentMessage {

    /**
     * 变更信息列表
     */
    @JsonProperty("messages")
    protected List<PlanChangeDetail> messages;

    @lombok.Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PlanChangeDetail {
        /**
         * 变更类型：STATUS_CHANGE, PLAN_CHANGE, TRY_REPLAN
         */
        @JsonProperty("change_type")
        private String changeType;

        // STATUS_CHANGE 和 TRY_REPLAN 字段
        /**
         * 阶段ID
         */
        @JsonProperty("stage_id")
        private String stageId;

        /**
         * 状态（STATUS_CHANGE时使用）
         */
        @JsonProperty("status")
        private String status;

        // PLAN_CHANGE 字段
        /**
         * 操作类型：ADD_STAGE, DELETE_STAGE, UPDATE_STAGE
         */
        @JsonProperty("operation")
        private String operation;

        /**
         * 阶段名称（PLAN_CHANGE时使用）
         */
        @JsonProperty("stage_name")
        private String stageName;

        /**
         * 阶段描述（PLAN_CHANGE时使用）
         */
        @JsonProperty("description")
        private String description;

        /**
         * 新增阶段的ID（ADD_STAGE时使用）
         */
        @JsonProperty("new_stage_id")
        private String newStageId;
    }
}
