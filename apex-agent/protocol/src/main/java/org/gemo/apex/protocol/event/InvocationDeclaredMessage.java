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
public class InvocationDeclaredMessage extends AgentMessage {
    @JsonProperty("messages")
    private List<InvocationMessage> messages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvocationMessage {
        @JsonProperty("invocation_id")
        private String invocationId;

        @JsonProperty("parent_invocation_id")
        private String parentInvocationId;

        @JsonProperty("name")
        private String name;

        @JsonProperty("invocation_type")
        private String invocationType;

        @JsonProperty("click_effect")
        private String clickEffect;

        @JsonProperty("content")
        private String content;

        @JsonProperty("complete")
        private boolean complete;

        @JsonProperty("render_type")
        private String renderType;
    }
}
