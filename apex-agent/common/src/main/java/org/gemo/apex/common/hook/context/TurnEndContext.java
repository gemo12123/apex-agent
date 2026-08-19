package org.gemo.apex.common.hook.context;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.shared.SharedDataStore;
import org.gemo.apex.common.snapshot.TurnSnapshot;

public record TurnEndContext(
        String sessionId, HookBinding binding, TurnSnapshot turn, SharedDataStore sharedData)
        implements HookContextView {
    public TurnEndContext {
        sessionId = required(sessionId, "sessionId");
        binding = nonNull(binding, "binding");
        turn = nonNull(turn, "turn");
        sharedData = nonNull(sharedData, "sharedData");
    }
}
