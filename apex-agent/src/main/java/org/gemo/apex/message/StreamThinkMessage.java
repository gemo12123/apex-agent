package org.gemo.apex.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * STREAM_THINK - 思考流消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class StreamThinkMessage extends AgentMessage {

    /**
     * 消息内容列表
     */
    @JsonProperty("messages")
    protected List<ContentMessage> messages;

    @lombok.Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ContentMessage {
        /**
         * 内容文本
         */
        @JsonProperty("content")
        private String content;
    }
}
