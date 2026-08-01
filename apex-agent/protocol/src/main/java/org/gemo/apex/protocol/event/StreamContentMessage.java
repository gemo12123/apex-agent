package org.gemo.apex.protocol.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class StreamContentMessage extends AgentMessage {
    @JsonProperty("messages")
    private List<ContentMessage> messages;

    @Data
    @NoArgsConstructor
    public static class ContentMessage {
        @JsonProperty("content") private String content;

        public ContentMessage(String content) {
            this.content = content;
        }
    }
}
