package org.gemo.apex.util;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.message.AgentMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 消息发送工具类，统一管理通过 SSE 向前端推送 AgentMessage 的逻辑。
 */
@Slf4j
public class MessageUtils {

    /**
     * 将 AgentMessage 序列化为 JSON 并通过 SSE 发送
     *
     * @param context      上下文（含 SseEmitter）
     * @param agentMessage 消息对象
     */
    public static void sendMessage(SuperAgentContext context, AgentMessage agentMessage) {
        if (agentMessage == null) {
            return;
        }
        sendMessage(context, JacksonUtils.toJson(agentMessage));
    }

    /**
     * 通过 SSE 发送已序列化的 JSON 字符串
     *
     * @param context      上下文（含 SseEmitter）
     * @param agentMessage JSON 字符串
     */
    public static void sendMessage(SuperAgentContext context, String agentMessage) {
        if (context == null || context.getSseEmitter() == null) {
            return;
        }
        sendMessage(context.getSseEmitter(), agentMessage);
    }

    /**
     * 底层 SSE 发送方法
     *
     * @param sseEmitter SSE 发送器
     * @param message    消息内容
     */
    public static void sendMessage(SseEmitter sseEmitter, String message) {
        if (StringUtils.isEmpty(message)) {
            return;
        }
        try {
            sseEmitter.send(message);
        } catch (IOException e) {
            log.warn("消息发送失败！", e);
        }
    }
}
