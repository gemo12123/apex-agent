package org.gemo.apex.domain.dto;

import lombok.Data;
import org.gemo.apex.constant.RequestType;

import java.util.Map;

/**
 * Web接口前端聊天请求实体
 */
@Data
public class ChatRequest {

    /**
     * 用户输入的提示信息
     */
    private String query;

    /**
     * 会话/任务ID
     */
    private String sessionId = "1";

    /**
     * 请求类型：NEW（新对话）或 HUMAN_RESPONSE（人类回复）
     */
    private RequestType type = RequestType.NEW;

    /**
     * 绑定的应用角色。默认为 null（或 "laifu"）可以对接后续指定特化代理。
     */
    private String agentKey = "default_agent";

    /**
     * 用户回复的内容（当 type=HUMAN_RESPONSE 时使用）
     */
    private Map<String, Object> humanResponse;
}
