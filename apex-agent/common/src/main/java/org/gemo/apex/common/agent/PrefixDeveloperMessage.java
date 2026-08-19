package org.gemo.apex.common.agent;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import org.gemo.apex.common.message.MessageRole;

/** 由 AGENT_BUILD 在请求内生成、位于根系统提示词之后的临时文本消息。 */
public record PrefixDeveloperMessage(MessageRole role, String content) {
    public PrefixDeveloperMessage {
        role = nonNull(role, "role");
        if (role != MessageRole.SYSTEM && role != MessageRole.USER) {
            throw new IllegalArgumentException("PrefixDeveloperMessage 仅支持 SYSTEM 或 USER");
        }
        content = required(content, "content");
    }
}
