package org.gemo.apex.kit.intervention;

import org.gemo.apex.common.intervention.QuestionSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AskHumanArgumentsParser {
    public List<QuestionSpec> parse(Map<String, Object> arguments) {
        Object rawQuestions = arguments == null ? null : arguments.get("questions");
        if (!(rawQuestions instanceof List<?> questions) || questions.isEmpty()) {
            throw new IllegalArgumentException("ask_human.questions 不能为空");
        }

        List<IndexedQuestion> parsed = new ArrayList<>(questions.size());
        for (Object rawQuestion : questions) {
            if (!(rawQuestion instanceof Map<?, ?> question)) {
                throw new IllegalArgumentException("ask_human.questions 只能包含对象");
            }
            int index = integer(question.get("index"), "index");
            String inputType = required(question.get("input_type"), "input_type");
            String text = required(question.get("question"), "question");
            String description = optional(question.get("description"));
            parsed.add(new IndexedQuestion(index,
                    new QuestionSpec(inputType, text, description, options(question.get("options")))));
        }
        parsed.sort(Comparator.comparingInt(IndexedQuestion::index));
        for (int i = 1; i < parsed.size(); i++) {
            if (parsed.get(i - 1).index() == parsed.get(i).index()) {
                throw new IllegalArgumentException("ask_human.questions.index 重复: " + parsed.get(i).index());
            }
        }
        return parsed.stream().map(IndexedQuestion::question).toList();
    }

    private List<Map<String, Object>> options(Object rawOptions) {
        if (rawOptions == null) return List.of();
        if (!(rawOptions instanceof List<?> options)) {
            throw new IllegalArgumentException("ask_human.questions.options 必须是数组");
        }
        List<Map<String, Object>> normalized = new ArrayList<>(options.size());
        for (Object rawOption : options) {
            if (!(rawOption instanceof Map<?, ?> option)) {
                throw new IllegalArgumentException("ask_human.questions.options 只能包含对象");
            }
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            option.forEach((key, value) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException("ask_human.questions.options key 必须是字符串");
                }
                copy.put(stringKey, value);
            });
            normalized.add(copy);
        }
        return List.copyOf(normalized);
    }

    private int integer(Object value, String field) {
        int result;
        if (value instanceof Number number) {
            result = number.intValue();
        } else {
            try {
                result = Integer.parseInt(String.valueOf(value));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("ask_human.questions." + field + " 必须是非负整数", exception);
            }
        }
        if (result < 0) throw new IllegalArgumentException("ask_human.questions." + field + " 不能小于 0");
        return result;
    }

    private String required(Object value, String field) {
        String text = optional(value);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("ask_human.questions." + field + " 不能为空");
        }
        return text;
    }

    private String optional(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record IndexedQuestion(int index, QuestionSpec question) {}
}
