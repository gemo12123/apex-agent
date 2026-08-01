package org.gemo.apex.common.hook.result;

import org.gemo.apex.common.intervention.HumanInterventionRequest;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record RequestHumanIntervention(HumanInterventionRequest request) implements PreToolCallHookResult {
    public RequestHumanIntervention { request = nonNull(request, "request"); }
}
