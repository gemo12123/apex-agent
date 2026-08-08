package org.gemo.apex.runtime.registry;

import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.extension.hook.*;

import java.util.*;

public final class HookRegistry implements HookResolver {
    private final Map<Key, LifecycleHook<?, ?>> hooks;

    public HookRegistry(Map<Key, LifecycleHook<?, ?>> h) {
        hooks = Map.copyOf(h);
    }

    public LifecycleHook<?, ?> resolve(HookPoint p, String n) {
        return hooks.get(new Key(p, n));
    }

    public record Key(HookPoint point, String name) {
    }
}
