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
public class StreamThinkMessage extends AgentMessage {
    @JsonProperty("messages")
    private List<ContentMessage> messages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentMessage {
        @JsonProperty("content")
        private String content;
    }
}
