package org.gemo.apex.runtime.event;

import java.util.concurrent.atomic.*;
import org.gemo.apex.extension.event.AgentEventPublisher;
import org.gemo.apex.protocol.event.*;
import org.gemo.apex.runtime.execution.RuntimeCancellationSource;

public final class OnceAgentEventPublisher implements AgentEventPublisher {
    private final AgentEventPublisher out;
    private final RuntimeCancellationSource cancel;
    private final AtomicBoolean ended = new AtomicBoolean();

    public OnceAgentEventPublisher(AgentEventPublisher o, RuntimeCancellationSource c) {
        out = o;
        cancel = c;
    }

    public void publish(AgentMessage m) {
        boolean end = m instanceof EndMessage;
        if (end && !ended.compareAndSet(false, true)) {
            return;
        }
        if (!end && ended.get()) {
            throw new IllegalStateException("事件流已结束");
        }
        try {
            out.publish(m);
        } catch (RuntimeException e) {
            cancel.cancel();
            throw e;
        }
    }

    public void end() {
        publish(EndMessage.builder().build());
    }
}
