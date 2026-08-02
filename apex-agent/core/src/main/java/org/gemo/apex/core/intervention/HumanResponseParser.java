package org.gemo.apex.core.intervention;

import org.gemo.apex.common.intervention.*;
import org.gemo.apex.common.snapshot.SuspendedToolCall;
import org.gemo.apex.core.exception.InvalidHumanResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HumanResponseParser {
    public HumanSubmission parse(HumanResponseCommand command, SuspendedToolCall suspended) {
        Map<String, Object> response = command.response();
        String type = string(response.get("interaction_type"), "interaction_type");
        if (suspended.intervention() instanceof QuestionInterventionRequest) {
            if (!"ASK_HUMAN".equals(type)) throw invalid("人工回复类型与挂起问题不匹配");
            Object rawAnswers = response.get("answers");
            if (!(rawAnswers instanceof Map<?, ?> values)) throw invalid("answers 必须是对象");
            Map<String, Object> answers = new LinkedHashMap<>();
            values.forEach((key, value) -> answers.put(String.valueOf(key), value));
            return new QuestionSubmission(suspended.toolCallId(), answers);
        }
        if (suspended.intervention() instanceof ToolConfirmationInterventionRequest confirmation) {
            if (!"TOOL_CONFIRMATION".equals(type)) throw invalid("人工回复类型与工具确认不匹配");
            String confirmationId = string(response.get("confirmation_id"), "confirmation_id");
            if (!confirmation.confirmationId().equals(confirmationId)) throw invalid("confirmation_id 不匹配");
            String rawDecision = string(response.get("decision"), "decision");
            ConfirmationDecision decision = switch (rawDecision.toUpperCase(java.util.Locale.ROOT)) {
                case "APPROVE", "CONFIRM" -> ConfirmationDecision.CONFIRM;
                case "DENY" -> ConfirmationDecision.DENY;
                default -> throw invalid("decision 只允许 APPROVE 或 DENY");
            };
            Object rawArguments = response.getOrDefault("updated_args", Map.of());
            if (!(rawArguments instanceof Map<?, ?> values)) throw invalid("updated_args 必须是对象");
            Map<String, Object> arguments = new LinkedHashMap<>();
            values.forEach((key, value) -> arguments.put(String.valueOf(key), value));
            return new ToolConfirmationSubmission(suspended.toolCallId(), confirmationId, decision, arguments);
        }
        throw invalid("未知人工介入类型");
    }

    private String string(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) throw invalid(field + " 不能为空");
        return text;
    }

    private InvalidHumanResponseException invalid(String message) {
        return new InvalidHumanResponseException(message);
    }
}
