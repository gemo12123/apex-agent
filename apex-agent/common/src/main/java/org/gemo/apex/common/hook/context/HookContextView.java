package org.gemo.apex.common.hook.context;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.shared.SharedDataStore;

public interface HookContextView {
    String sessionId();

    HookBinding binding();

    SharedDataStore sharedData();
}
