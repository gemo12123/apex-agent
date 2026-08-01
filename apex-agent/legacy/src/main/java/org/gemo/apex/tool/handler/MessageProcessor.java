package org.gemo.apex.tool.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.message.AgentMessage;
import org.gemo.apex.tool.handler.exception.MessageDeserializationException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息处理器
 * 协调反序列化、路由、处理等流程
 *
 * @author gemo
 */
@Slf4j
@Component
public class MessageProcessor {

    private final ObjectMapper objectMapper;
    private final MessageTypeRouter router;
    private final List<MessageHandler> handlers;

    /**
     * 初始化时注册所有 Handler
     */
    public MessageProcessor(
            ObjectMapper objectMapper,
            MessageTypeRouter router,
            List<MessageHandler> handlers) {
        this.objectMapper = objectMapper;
        this.router = router;
        this.handlers = handlers;

        // 注册所有 Handler
        handlers.forEach(router::registerHandler);
        log.info("已注册 {} 个消息处理器", handlers.size());
    }

    /**
     * 处理单行消息
     *
     * @param line    JSON 字符串
     * @param context 处理上下文
     */
    public void process(String line, MessageHandlerContext context) {
        try {
            // 1. 反序列化
            AgentMessage message = deserializeMessage(line);

            // 2. 路由到具体处理器
            router.process(message, context);

            // 3. 检查是否完成
            if (context.isComplete()) {
                log.debug("消息处理完成，收到 END 消息");
            }

        } catch (Exception e) {
            log.error("处理消息行失败: {}", line, e);
        }
    }

    /**
     * 反序列化消息
     *
     * @param line JSON 字符串
     * @return AgentMessage 对象
     */
    private AgentMessage deserializeMessage(String line) {
        try {
            return objectMapper.readValue(line, AgentMessage.class);
        } catch (Exception e) {
            throw new MessageDeserializationException(
                    "Failed to deserialize message: " + line, e);
        }
    }
}
