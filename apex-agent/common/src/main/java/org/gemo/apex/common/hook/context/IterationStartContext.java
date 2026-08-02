package org.gemo.apex.common.hook.context;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.snapshot.TurnSnapshot;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record IterationStartContext(String sessionId, HookBinding binding,
                                    TurnSnapshot turn) implements HookContextView {
    public IterationStartContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        turn = nonNull(turn, "turn");
    }
}
