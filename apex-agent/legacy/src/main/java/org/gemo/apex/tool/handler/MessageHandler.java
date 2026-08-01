package org.gemo.apex.tool.handler;

import org.gemo.apex.message.AgentMessage;

/**
 * 消息处理器接口
 * 使用策略模式处理不同类型的 AgentMessage
 *
 * @author gemo
 */
public interface MessageHandler extends Comparable<MessageHandler> {

    /**
     * 判断是否能处理该消息
     *
     * @param message 消息对象
     * @return 是否能处理
     */
    boolean canHandle(AgentMessage message);

    /**
     * 处理消息
     *
     * @param message 消息对象
     * @param context 处理上下文
     */
    void handle(AgentMessage message, MessageHandlerContext context);

    /**
     * 获取处理器优先级（用于责任链排序）
     * 数值越小优先级越高
     *
     * @return 优先级
     */
    default int getOrder() {
        return 100;
    }

    @Override
    default int compareTo(MessageHandler other) {
        return Integer.compare(this.getOrder(), other.getOrder());
    }
}
