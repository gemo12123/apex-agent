package org.gemo.apex.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * STREAM_CONTENT - 正文流消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class StreamContentMessage extends AgentMessage {

    /**
     * 消息内容列表
     */
    @JsonProperty("messages")
    protected List<StreamThinkMessage.ContentMessage> messages;
}
