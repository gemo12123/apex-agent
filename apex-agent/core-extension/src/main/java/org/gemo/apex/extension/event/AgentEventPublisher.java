package org.gemo.apex.extension.event;

import org.gemo.apex.protocol.event.AgentMessage;

public interface AgentEventPublisher {
    void publish(AgentMessage message);
}
