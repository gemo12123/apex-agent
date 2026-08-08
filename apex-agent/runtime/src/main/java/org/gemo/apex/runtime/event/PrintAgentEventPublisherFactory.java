package org.gemo.apex.runtime.event;

import org.gemo.apex.common.execution.AgentExecutionDescriptor;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.extension.event.*;

public final class PrintAgentEventPublisherFactory implements AgentEventPublisherFactory {
    public AgentEventPublisher create(AgentExecutionDescriptor d) {
        return m -> System.out.println(JsonUtils.toJson(m));
    }
}
