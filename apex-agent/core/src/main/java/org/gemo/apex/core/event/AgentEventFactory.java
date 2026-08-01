package org.gemo.apex.core.event;

import org.gemo.apex.common.intervention.QuestionInterventionRequest;
import org.gemo.apex.common.intervention.ToolConfirmationInterventionRequest;
import org.gemo.apex.protocol.event.*;
import org.gemo.apex.protocol.event.detail.AskHumanDetail;
import org.gemo.apex.protocol.event.detail.AskHumanOption;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentEventFactory {
    public StreamContentMessage streamContent(String contentId, String delta) {
        return StreamContentMessage.builder()
                .context(context(Map.of("content_id", required(contentId, "contentId"))))
                .messages(List.of(new StreamContentMessage.ContentMessage(delta)))
                .build();
    }

    public AskHumanMessage askHuman(QuestionInterventionRequest request,
                                    String invocationId, String executor) {
        List<AskHumanDetail> details = new java.util.ArrayList<>();
        for (var question : request.questions()) {
            List<AskHumanOption> options = new java.util.ArrayList<>();
            for (Map<String, Object> option : question.options()) {
                options.add(AskHumanOption.builder().label(String.valueOf(option.get("label")))
                        .description(option.get("description") == null ? null
                                : String.valueOf(option.get("description"))).build());
            }
            details.add(AskHumanDetail.builder().inputType(question.inputType()).question(question.question())
                    .description(question.description()).options(options)
                    .toolCallId(request.toolCallId()).build());
        }
        return AskHumanMessage.builder()
                .context(context(Map.of("executor", required(executor, "executor"),
                        "invocation_id", required(invocationId, "invocationId"))))
                .messages(details).build();
    }

    public ToolConfirmationMessage toolConfirmation(ToolConfirmationInterventionRequest request) {
        return ToolConfirmationMessage.builder()
                .context(context(Map.of("executor", request.toolName(),
                        "invocation_id", request.invocationId())))
                .messages(List.of(request.presentation())).build();
    }

    public EndMessage end() { return EndMessage.builder().build(); }

    private Map<String, Object> context(Map<String, Object> extra) {
        Map<String, Object> result = new LinkedHashMap<>(extra);
        result.put("mode", "react");
        return Map.copyOf(result);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return value;
    }
}
