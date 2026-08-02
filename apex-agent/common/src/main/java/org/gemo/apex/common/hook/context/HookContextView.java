package org.gemo.apex.common.hook.context;

import org.gemo.apex.common.hook.HookBinding;

public interface HookContextView {
    String sessionId();
    HookBinding binding();
}
