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
public class InvocationChangeMessage extends AgentMessage {
    @JsonProperty("messages")
    private List<InvocationChangeDetail> messages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvocationChangeDetail {
        @JsonProperty("change_type")
        private String changeType;

        @JsonProperty("invocation_id")
        private String invocationId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("content")
        private String content;

        @JsonProperty("render_type")
        private String renderType;
    }
}
