package org.gemo.apex.common.tool;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.gemo.apex.common.intervention.HumanSubmission;
import org.gemo.apex.common.support.DomainValues;

import java.util.Map;

import static org.gemo.apex.common.support.DomainValues.*;

public record ToolExecutionContext(String sessionId, long turnNo, int iterationNo, String userId,
                                   HumanSubmission humanSubmission, SubAgentCallTrace subAgentCallTrace,
                                   @JsonIgnore CancellationToken cancellationToken,
                                   Map<String, Object> attributes) {
    public ToolExecutionContext {
        sessionId = required(sessionId, "sessionId");
        nonNegative(turnNo, "turnNo");
        nonNegative(iterationNo, "iterationNo");
        userId = required(userId, "userId");
        cancellationToken = nonNull(cancellationToken, "cancellationToken");
        attributes = DomainValues.immutableMap(attributes, "attributes");
    }
}
