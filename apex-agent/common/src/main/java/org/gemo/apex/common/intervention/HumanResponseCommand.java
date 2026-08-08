package org.gemo.apex.common.intervention;

import static org.gemo.apex.common.support.DomainValues.required;

import java.util.Map;
import org.gemo.apex.common.support.DomainValues;

public record HumanResponseCommand(
        String sessionId, String agentKey, String userId, Map<String, Object> response) {
    public HumanResponseCommand {
        sessionId = required(sessionId, "sessionId");
        agentKey = required(agentKey, "agentKey");
        userId = required(userId, "userId");
        response = DomainValues.immutableMap(response, "response");
    }
}
