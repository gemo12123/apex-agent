package org.gemo.apex.protocol.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class PlanChangeMessage extends AgentMessage {
    @JsonProperty("messages")
    private List<PlanChangeDetail> messages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanChangeDetail {
        @JsonProperty("change_type")
        private String changeType;

        @JsonProperty("stage_id")
        private String stageId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("operation")
        private String operation;

        @JsonProperty("stage_name")
        private String stageName;

        @JsonProperty("description")
        private String description;

        @JsonProperty("new_stage_id")
        private String newStageId;
    }
}
