package org.gemo.apex.hook.lifecycle;

import lombok.Builder;
import lombok.Getter;
import org.springframework.ai.chat.messages.Message;

@Getter
@Builder
public class MessageOperation {

    public enum Type {
        APPEND,
        DELETE,
        REPLACE
    }

    private final Type type;
    private final Integer index;
    private final Message message;

    public static MessageOperation append(Message message) {
        return builder().type(Type.APPEND).message(message).build();
    }

    public static MessageOperation delete(int index) {
        return builder().type(Type.DELETE).index(index).build();
    }

    public static MessageOperation replace(int index, Message message) {
        return builder().type(Type.REPLACE).index(index).message(message).build();
    }
}
