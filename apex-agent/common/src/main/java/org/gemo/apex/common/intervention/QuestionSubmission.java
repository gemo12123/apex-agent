package org.gemo.apex.common.intervention;

import org.gemo.apex.common.support.DomainValues;

import java.util.Map;

import static org.gemo.apex.common.support.DomainValues.required;

public record QuestionSubmission(String toolCallId, Map<String, Object> answers) implements HumanSubmission {
    public QuestionSubmission {
        toolCallId = required(toolCallId, "toolCallId");
        answers = DomainValues.immutableMap(answers, "answers");
    }
}
