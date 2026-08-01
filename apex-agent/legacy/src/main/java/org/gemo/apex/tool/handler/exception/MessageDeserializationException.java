package org.gemo.apex.tool.handler.exception;

/**
 * 消息反序列化异常
 *
 * @author gemo
 */
public class MessageDeserializationException extends MessageProcessingException {

    public MessageDeserializationException(String message) {
        super(message);
    }

    public MessageDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
