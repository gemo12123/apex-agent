package org.gemo.apex.common.hook.context;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.snapshot.SessionSnapshot;

public record TurnStartContext(String sessionId, HookBinding binding, SessionSnapshot session)
        implements HookContextView {
    public TurnStartContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        session = nonNull(session, "session");
    }
}
