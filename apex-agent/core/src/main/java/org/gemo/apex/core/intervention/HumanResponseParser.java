package org.gemo.apex.core.intervention;

import org.gemo.apex.common.intervention.*;
import org.gemo.apex.core.exception.InvalidHumanResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 在恢复具体挂起项时解析显式回复，或即时生成该项的默认语义。 */
public final class HumanResponseParser {
    public HumanSubmission parse(Object rawResponse, HumanInterventionRequest intervention) {
        if (intervention instanceof QuestionInterventionRequest question) {
            return parseQuestion(rawResponse, question);
        }
        if (intervention instanceof ToolConfirmationInterventionRequest confirmation) {
            return parseConfirmation(rawResponse, confirmation);
        }
        throw invalid("未知人工介入类型");
    }

    private QuestionSubmission parseQuestion(Object rawResponse, QuestionInterventionRequest request) {
        Map<?, ?> response = optionalObject(rawResponse);
        Map<?, ?> suppliedAnswers = Map.of();
        if (response != null) {
            requireType(response, "ASK_HUMAN", "人工回复类型与挂起问题不匹配");
            Object rawAnswers = response.get("answers");
            if (!(rawAnswers instanceof Map<?, ?> values)) throw invalid("answers 必须是对象");
            suppliedAnswers = values;
            for (Object key : suppliedAnswers.keySet()) {
                int index = answerIndex(key);
                if (index < 0 || index >= request.questions().size()) {
                    throw invalid("answers 包含未知问题索引: " + key);
                }
            }
        }
        Map<String, Object> answers = new LinkedHashMap<>();
        for (int index = 0; index < request.questions().size(); index++) {
            QuestionSpec question = request.questions().get(index);
            Object supplied = suppliedAnswers.get(String.valueOf(index));
            answers.put(String.valueOf(index), answer(question, supplied));
        }
        return new QuestionSubmission(request.toolCallId(), answers);
    }

    private ToolConfirmationSubmission parseConfirmation(
            Object rawResponse, ToolConfirmationInterventionRequest request) {
        Map<?, ?> response = optionalObject(rawResponse);
        if (response == null) {
            return new ToolConfirmationSubmission(request.toolCallId(), request.confirmationId(),
                    ConfirmationDecision.CONFIRM, Map.of());
        }
        requireType(response, "TOOL_CONFIRMATION", "人工回复类型与工具确认不匹配");
        String confirmationId = string(response.get("confirmation_id"), "confirmation_id");
        if (!request.confirmationId().equals(confirmationId)) throw invalid("confirmation_id 不匹配");
        String rawDecision = string(response.get("decision"), "decision");
        ConfirmationDecision decision = switch (rawDecision.toUpperCase(Locale.ROOT)) {
            case "APPROVE", "CONFIRM" -> ConfirmationDecision.CONFIRM;
            case "DENY" -> ConfirmationDecision.DENY;
            default -> throw invalid("decision 只允许 APPROVE 或 DENY");
        };
        Object rawArguments = response.containsKey("updated_args")
                ? response.get("updated_args") : Map.of();
        if (!(rawArguments instanceof Map<?, ?> values)) throw invalid("updated_args 必须是对象");
        Map<String, Object> arguments = new LinkedHashMap<>();
        values.forEach((key, value) -> arguments.put(String.valueOf(key), value));
        return new ToolConfirmationSubmission(request.toolCallId(), confirmationId, decision, arguments);
    }

    private Object answer(QuestionSpec question, Object supplied) {
        return switch (question.inputType()) {
            case "MULTI_SELECT" -> multiAnswer(question, supplied);
            case "SINGLE_SELECT" -> textAnswer(supplied, firstOption(question));
            case "CONFIRM" -> textAnswer(supplied, "确认");
            case "TEXT_INPUT" -> textAnswer(supplied, "用户未提供输入");
            default -> throw invalid("不支持的问题输入类型: " + question.inputType());
        };
    }

    private Object multiAnswer(QuestionSpec question, Object supplied) {
        if (supplied == null) return List.of(firstOption(question));
        if (!(supplied instanceof List<?> values)) throw invalid("MULTI_SELECT 答案必须是字符串数组");
        List<String> normalized = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String text)) throw invalid("MULTI_SELECT 答案必须是字符串数组");
            if (!text.isBlank()) normalized.add(text);
        }
        return normalized.isEmpty() ? List.of(firstOption(question)) : List.copyOf(normalized);
    }

    private String textAnswer(Object supplied, String fallback) {
        if (supplied == null) return fallback;
        if (!(supplied instanceof String text)) throw invalid("问题答案必须是字符串");
        return text.isBlank() ? fallback : text;
    }

    private String firstOption(QuestionSpec question) {
        if (question.options().isEmpty()) return "用户未提供选择";
        Object label = question.options().getFirst().get("label");
        return label == null || String.valueOf(label).isBlank()
                ? "用户未提供选择" : String.valueOf(label);
    }

    private Map<?, ?> optionalObject(Object value) {
        if (value == null) return null;
        if (!(value instanceof Map<?, ?> map)) throw invalid("人工回复项必须是对象");
        return map;
    }

    private void requireType(Map<?, ?> response, String expected, String mismatchMessage) {
        String type = string(response.get("interaction_type"), "interaction_type");
        if (!expected.equals(type)) throw invalid(mismatchMessage);
    }

    private int answerIndex(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw invalid("answers 问题索引必须是整数");
        }
    }

    private String string(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) throw invalid(field + " 不能为空");
        return text;
    }

    private InvalidHumanResponseException invalid(String message) {
        return new InvalidHumanResponseException(message);
    }
}
