package org.gemo.apex.core.event;

import java.util.Objects;
import org.gemo.apex.extension.event.AgentEventPublisher;
import org.gemo.apex.protocol.event.AgentMessage;

public final class AgentEventEmitter {
    private final AgentEventPublisher publisher;
    private final AgentEventFactory factory;
    private boolean taskErrorRequested;
    private boolean endRequested;

    public AgentEventEmitter(AgentEventPublisher publisher, AgentEventFactory factory) {
        this.publisher = Objects.requireNonNull(publisher);
        this.factory = Objects.requireNonNull(factory);
    }

    public void publish(AgentMessage message) {
        publisher.publish(message);
    }

    public void requestEnd() {
        if (!endRequested) {
            endRequested = true;
            publisher.publish(factory.end());
        }
    }

    public void requestTaskError(Throwable error) {
        if (!taskErrorRequested) {
            taskErrorRequested = true;
            publisher.publish(factory.taskError(error));
        }
    }
}
