package org.gemo.apex.common.tool;

import static org.gemo.apex.common.support.DomainValues.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;
import java.util.Set;
import org.gemo.apex.common.intervention.HumanSubmission;
import org.gemo.apex.common.support.DomainValues;

public record ToolExecutionContext(
        String sessionId,
        long turnNo,
        int iterationNo,
        String userId,
        Set<String> enabledSkills,
        Set<String> activatedSkills,
        HumanSubmission humanSubmission,
        SubAgentCallTrace subAgentCallTrace,
        @JsonIgnore CancellationToken cancellationToken,
        Map<String, Object> attributes) {
    public ToolExecutionContext {
        sessionId = required(sessionId, "sessionId");
        nonNegative(turnNo, "turnNo");
        nonNegative(iterationNo, "iterationNo");
        userId = required(userId, "userId");
        enabledSkills = immutableNames(enabledSkills, "enabledSkills");
        activatedSkills = immutableNames(activatedSkills, "activatedSkills");
        cancellationToken = nonNull(cancellationToken, "cancellationToken");
        attributes = DomainValues.immutableMap(attributes, "attributes");
    }

    public ToolExecutionContext(
            String sessionId,
            long turnNo,
            int iterationNo,
            String userId,
            HumanSubmission humanSubmission,
            SubAgentCallTrace subAgentCallTrace,
            CancellationToken cancellationToken,
            Map<String, Object> attributes) {
        this(
                sessionId,
                turnNo,
                iterationNo,
                userId,
                Set.of(),
                Set.of(),
                humanSubmission,
                subAgentCallTrace,
                cancellationToken,
                attributes);
    }
}
