package org.gemo.apex.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * END - 会话结束消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class EndMessage extends AgentMessage {

    /**
     * 消息列表（结束消息时为空）
     */
    @JsonProperty("messages")
    protected List<Object> messages;
}
