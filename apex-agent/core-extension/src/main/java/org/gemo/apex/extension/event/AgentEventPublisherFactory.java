package org.gemo.apex.extension.event;

import org.gemo.apex.common.execution.AgentExecutionDescriptor;

public interface AgentEventPublisherFactory {
    /**
     * 为单次请求创建独立事件出口，不得复用带请求状态的 Publisher。
     */
    AgentEventPublisher create(AgentExecutionDescriptor execution);
}
