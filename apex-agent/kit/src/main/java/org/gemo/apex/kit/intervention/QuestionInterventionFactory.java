package org.gemo.apex.kit.intervention;

import java.util.Objects;
import org.gemo.apex.common.intervention.QuestionInterventionRequest;
import org.gemo.apex.common.tool.ToolCall;

public final class QuestionInterventionFactory {
    private final AskHumanArgumentsParser parser;

    public QuestionInterventionFactory() {
        this(new AskHumanArgumentsParser());
    }

    public QuestionInterventionFactory(AskHumanArgumentsParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public QuestionInterventionRequest create(ToolCall call) {
        Objects.requireNonNull(call, "call");
        return new QuestionInterventionRequest(call.toolCallId(), parser.parse(call.arguments()));
    }
}
