package org.gemo.apex.tool.handler;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.message.AgentMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 消息类型路由器
 * 管理所有 MessageHandler，根据消息类型找到合适的处理器
 *
 * @author gemo
 */
@Slf4j
@Component
public class MessageTypeRouter {

    private final List<MessageHandler> handlers = new ArrayList<>();

    /**
     * 注册处理器
     *
     * @param handler 处理器
     */
    public void registerHandler(MessageHandler handler) {
        handlers.add(handler);
        handlers.sort(Comparator.naturalOrder());
        log.info("注册消息处理器: {}", handler.getClass().getSimpleName());
    }

    /**
     * 查找合适的处理器
     *
     * @param message 消息对象
     * @return 处理器（Optional）
     */
    public Optional<MessageHandler> findHandler(AgentMessage message) {
        return handlers.stream()
                .filter(handler -> handler.canHandle(message))
                .findFirst();
    }

    /**
     * 处理消息
     *
     * @param message 消息对象
     * @param context 处理上下文
     */
    public void process(AgentMessage message, MessageHandlerContext context) {
        findHandler(message).ifPresentOrElse(
                handler -> handler.handle(message, context),
                () -> log.warn("未找到对应的消息处理器: {}", message.getClass().getSimpleName()));
    }
}
