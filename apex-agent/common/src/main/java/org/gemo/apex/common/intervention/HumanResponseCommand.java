package org.gemo.apex.common.intervention;

import org.gemo.apex.common.support.DomainValues;

import java.util.Map;

import static org.gemo.apex.common.support.DomainValues.required;

public record HumanResponseCommand(String sessionId, String agentKey, String userId,
                                   Map<String, Object> response) {
    public HumanResponseCommand {
        sessionId = required(sessionId, "sessionId");
        agentKey = required(agentKey, "agentKey");
        userId = required(userId, "userId");
        response = DomainValues.immutableMap(response, "response");
    }
}
