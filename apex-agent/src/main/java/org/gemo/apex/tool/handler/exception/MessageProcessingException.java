package org.gemo.apex.tool.handler.exception;

/**
 * 消息处理异常基类
 *
 * @author gemo
 */
public class MessageProcessingException extends RuntimeException {

    public MessageProcessingException(String message) {
        super(message);
    }

    public MessageProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
