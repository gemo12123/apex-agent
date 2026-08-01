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
public class TaskThinkDeclaredMessage extends AgentMessage {
    @JsonProperty("messages") private List<TaskThinkDeclaredDetail> messages;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TaskThinkDeclaredDetail {
        @JsonProperty("task_id") private String taskId;
        @JsonProperty("content") private String content;
    }
}
