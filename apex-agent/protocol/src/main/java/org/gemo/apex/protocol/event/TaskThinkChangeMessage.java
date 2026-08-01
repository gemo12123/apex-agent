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
public class TaskThinkChangeMessage extends AgentMessage {
    @JsonProperty("messages") private List<TaskThinkChangeDetail> messages;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TaskThinkChangeDetail {
        @JsonProperty("change_type") private String changeType;
        @JsonProperty("task_id") private String taskId;
        @JsonProperty("status") private String status;
        @JsonProperty("content") private String content;
    }
}
