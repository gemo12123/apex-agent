package org.gemo.apex.core.intervention;

import org.gemo.apex.common.intervention.QuestionInterventionRequest;
import org.gemo.apex.common.intervention.ToolConfirmationInterventionRequest;
import org.gemo.apex.common.snapshot.SuspendedToolCall;
import org.gemo.apex.common.snapshot.SuspensionPoint;
import org.gemo.apex.core.agent.ApexAgentContext;
import org.gemo.apex.core.event.AgentEventEmitter;
import org.gemo.apex.core.event.AgentEventFactory;
import org.gemo.apex.core.exception.SuspensionEventPublishException;

import java.util.List;

/**
 * 将执行切换为人工介入状态。
 *
 * <p>必须先保存包含挂起 ToolCall 的快照，后发布 SSE 交互事件；这样客户端即使先断开，恢复请求
 * 也能从持久化状态继续。</p>
 */
public final class InterventionSuspender {
    private final AgentEventEmitter emitter;
    private final AgentEventFactory events;

    public InterventionSuspender(AgentEventEmitter emitter, AgentEventFactory events) {
        this.emitter = emitter;
        this.events = events;
    }

    /** 记录挂起点、持久化会话，再发布提问或确认事件。 */
    public void suspend(ApexAgentContext context, String invocationId,
                        org.gemo.apex.common.intervention.HumanInterventionRequest intervention,
                        List<String> executedHookIds, boolean replaceExisting) {
        var call = context.toolCall();
        SuspendedToolCall suspended = new SuspendedToolCall(context.snapshot().sessionId(),
                context.snapshot().currentTurnNo(),
                context.snapshot().activeTurn().currentIteration().iterationNo(), call.toolCallId(), invocationId,
                call.name(), call.arguments(), intervention, executedHookIds, SuspensionPoint.PRE_TOOL_CALL);
        context.suspend(suspended, replaceExisting);
        context.save();
        try {
            if (intervention instanceof QuestionInterventionRequest question) {
                emitter.publish(events.askHuman(question, invocationId, call.name()));
            } else if (intervention instanceof ToolConfirmationInterventionRequest confirmation) {
                emitter.publish(events.toolConfirmation(confirmation));
            } else {
                throw new IllegalArgumentException("不支持的人工介入类型");
            }
            emitter.requestEnd();
        } catch (RuntimeException error) {
            throw new SuspensionEventPublishException(error);
        }
    }
}
