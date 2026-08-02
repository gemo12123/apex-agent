package org.gemo.apex.platform.config;

import org.gemo.apex.extension.hook.LifecycleHook;

public record PlatformHookRegistration(String stableName, LifecycleHook<?, ?> hook) {
    public PlatformHookRegistration {
        if (stableName == null || stableName.isBlank()) throw new IllegalArgumentException("stableName 不能为空");
        if (hook == null) throw new IllegalArgumentException("hook 不能为空");
    }
}
