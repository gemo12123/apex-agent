package org.gemo.apex.hook.lifecycle;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.springframework.ai.chat.messages.Message;

@Data
@Builder
@Jacksonized
public class MessageMutationRecord {
    private HookPoint hookPoint;
    private String hookBean;
    private MessageOperation.Type operation;
    private Integer index;
    private Message beforeMessage;
    private Message afterMessage;
    private boolean applied;
    private String error;
}
