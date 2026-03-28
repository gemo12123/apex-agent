package org.gemo.apex.tool.handler;

import lombok.Builder;
import lombok.Data;
import org.gemo.apex.context.SuperAgentContext;

import java.util.Map;
import java.util.Optional;

/**
 * 消息处理上下文
 * 封装处理过程中需要的所有状态和依赖
 *
 * @author gemo
 */
@Data
@Builder
public class MessageHandlerContext {

    /**
     * 结果累积器
     */
    private final StringBuilder resultBuilder;

    /**
     * SuperAgent 上下文（可选）
     */
    private final Optional<SuperAgentContext> superAgentContext;

    /**
     * Tool 调用上下文（可选），传递 stage_id、agent_name 等信息
     */
    private final Optional<Map<String, Object>> toolContext;

    /**
     * 是否已完成处理
     */
    @Builder.Default
    private boolean complete = false;

    /**
     * 标记处理完成
     */
    public void markComplete() {
        this.complete = true;
    }
}
