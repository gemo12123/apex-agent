package org.gemo.apex.common.intervention;

import org.gemo.apex.common.support.DomainValues;

import java.util.List;
import java.util.Map;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.required;

public record QuestionSpec(String inputType, String question, String description,
                           List<Map<String, Object>> options) {
    public QuestionSpec {
        inputType = required(inputType, "inputType");
        question = required(question, "question");
        options = immutableList(options, "options").stream()
                .map(option -> DomainValues.immutableMap(option, "options"))
                .toList();
    }
}
