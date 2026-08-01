package org.gemo.apex.core.event;

import org.gemo.apex.extension.event.AgentEventPublisher;
import org.gemo.apex.protocol.event.AgentMessage;

public final class AgentEventEmitter {
    private final AgentEventPublisher publisher;
    private final AgentEventFactory factory;
    private boolean endRequested;

    public AgentEventEmitter(AgentEventPublisher publisher, AgentEventFactory factory) {
        this.publisher = java.util.Objects.requireNonNull(publisher);
        this.factory = java.util.Objects.requireNonNull(factory);
    }

    public void publish(AgentMessage message) { publisher.publish(message); }

    public void requestEnd() {
        if (!endRequested) {
            endRequested = true;
            publisher.publish(factory.end());
        }
    }
}
