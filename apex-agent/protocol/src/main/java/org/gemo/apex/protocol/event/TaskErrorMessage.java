package org.gemo.apex.protocol.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class TaskErrorMessage extends AgentMessage {
    @JsonProperty("messages")
    private List<ErrorDetail> messages;

    @Data
    @NoArgsConstructor
    public static class ErrorDetail {
        @JsonProperty("message")
        private String message;

        public ErrorDetail(String message) {
            this.message = message;
        }
    }
}
