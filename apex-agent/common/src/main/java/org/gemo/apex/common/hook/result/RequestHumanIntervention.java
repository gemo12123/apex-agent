package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.intervention.HumanInterventionRequest;

public record RequestHumanIntervention(HumanInterventionRequest request)
        implements PreToolCallHookResult {
    public RequestHumanIntervention {
        request = nonNull(request, "request");
    }
}
