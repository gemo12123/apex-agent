package org.gemo.apex.protocol.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data @EqualsAndHashCode(callSuper = true) @SuperBuilder @NoArgsConstructor
public class PlanDeclaredMessage extends AgentMessage {
    @JsonProperty("messages") private List<StageMessage> messages;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StageMessage {
        @JsonProperty("stage_id") private String stageId;
        @JsonProperty("stage_name") private String stageName;
        @JsonProperty("description") private String description;
        @JsonProperty("status") private String status;
    }
}
