package org.gemo.apex.tool.handler;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.message.AgentMessage;
import org.gemo.apex.tool.handler.exception.MessageProcessingException;

/**
 * 消息处理器抽象基类
 * 提供通用的日志和异常处理
 *
 * @author gemo
 */
@Slf4j
public abstract class AbstractMessageHandler implements MessageHandler {

    @Override
    public void handle(AgentMessage message, MessageHandlerContext context) {
        String messageType = message.getClass().getSimpleName();

        try {
            doHandle(message, context);
        } catch (Exception e) {
            log.error("处理消息失败: {}", messageType, e);
            throw new MessageProcessingException(
                    "Failed to process message: " + messageType, e);
        }
    }

    /**
     * 实际的处理逻辑，由子类实现
     *
     * @param message 消息对象
     * @param context 处理上下文
     */
    protected abstract void doHandle(AgentMessage message, MessageHandlerContext context);
}
