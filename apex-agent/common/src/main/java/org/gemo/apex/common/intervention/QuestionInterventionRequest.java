package org.gemo.apex.common.intervention;

import static org.gemo.apex.common.support.DomainValues.immutableList;
import static org.gemo.apex.common.support.DomainValues.required;

import java.util.List;

public record QuestionInterventionRequest(String toolCallId, List<QuestionSpec> questions)
        implements HumanInterventionRequest {
    public QuestionInterventionRequest {
        toolCallId = required(toolCallId, "toolCallId");
        questions = immutableList(questions, "questions");
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("questions 不能为空");
        }
    }
}
