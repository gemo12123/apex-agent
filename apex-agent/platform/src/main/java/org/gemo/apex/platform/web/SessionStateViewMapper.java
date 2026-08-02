package org.gemo.apex.platform.web;

import org.gemo.apex.common.intervention.QuestionInterventionRequest;
import org.gemo.apex.common.intervention.ToolConfirmationInterventionRequest;
import org.gemo.apex.common.snapshot.SessionSnapshot;
import org.gemo.apex.protocol.event.AgentMessage;
import org.gemo.apex.protocol.event.AskHumanMessage;
import org.gemo.apex.protocol.event.ToolConfirmationMessage;
import org.gemo.apex.protocol.event.detail.AskHumanDetail;
import org.gemo.apex.protocol.event.detail.AskHumanOption;
import org.gemo.apex.protocol.request.SessionStateView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SessionStateViewMapper {
    public SessionStateView map(SessionSnapshot snapshot) {
        AgentMessage pending = snapshot.suspendedToolCall() == null ? null : interaction(snapshot);
        return new SessionStateView(snapshot.sessionId(), snapshot.agentKey(), snapshot.status().name(), pending);
    }

    private AgentMessage interaction(SessionSnapshot snapshot) {
        var suspended = snapshot.suspendedToolCall();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("mode", "react");
        context.put("executor", suspended.toolName());
        context.put("invocation_id", suspended.invocationId());
        if (suspended.intervention() instanceof QuestionInterventionRequest question) {
            List<AskHumanDetail> details = new ArrayList<>();
            question.questions().forEach(spec -> {
                List<AskHumanOption> options = new ArrayList<>();
                spec.options().forEach(option -> options.add(AskHumanOption.builder()
                        .label(String.valueOf(option.get("label")))
                        .description(option.get("description") == null ? null
                                : String.valueOf(option.get("description"))).build()));
                details.add(AskHumanDetail.builder().inputType(spec.inputType()).question(spec.question())
                        .description(spec.description()).options(options)
                        .toolCallId(question.toolCallId()).build());
            });
            return AskHumanMessage.builder().context(Map.copyOf(context)).messages(details).build();
        }
        if (suspended.intervention() instanceof ToolConfirmationInterventionRequest confirmation) {
            return ToolConfirmationMessage.builder().context(Map.copyOf(context))
                    .messages(List.of(confirmation.presentation())).build();
        }
        throw new IllegalStateException("不支持的挂起交互类型");
    }
}
