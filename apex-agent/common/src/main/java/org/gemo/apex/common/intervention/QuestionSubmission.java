package org.gemo.apex.common.intervention;

import static org.gemo.apex.common.support.DomainValues.required;

import java.util.Map;
import org.gemo.apex.common.support.DomainValues;

public record QuestionSubmission(String toolCallId, Map<String, Object> answers)
        implements HumanSubmission {
    public QuestionSubmission {
        toolCallId = required(toolCallId, "toolCallId");
        answers = DomainValues.immutableMap(answers, "answers");
    }
}
