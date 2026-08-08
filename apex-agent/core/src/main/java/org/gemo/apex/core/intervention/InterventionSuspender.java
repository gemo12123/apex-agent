package org.gemo.apex.core.intervention;

import org.gemo.apex.common.snapshot.SuspendedToolBatch;
import org.gemo.apex.core.agent.ApexAgentContext;
import org.gemo.apex.core.event.AgentEventEmitter;
import org.gemo.apex.core.event.AgentEventFactory;
import org.gemo.apex.core.exception.SuspensionEventPublishException;

/** 先保存完整工具批次，再发布唯一的人工介入事件。 */
public final class InterventionSuspender {
    private final AgentEventEmitter emitter;
    private final AgentEventFactory events;

    public InterventionSuspender(AgentEventEmitter emitter, AgentEventFactory events) {
        this.emitter = emitter;
        this.events = events;
    }

    public void suspend(ApexAgentContext context, SuspendedToolBatch batch, boolean replaceExisting) {
        context.suspend(batch, replaceExisting);
        context.save();
        try {
            emitter.publish(events.humanIntervention(batch));
            emitter.requestEnd();
        } catch (RuntimeException error) {
            throw new SuspensionEventPublishException(error);
        }
    }
}
