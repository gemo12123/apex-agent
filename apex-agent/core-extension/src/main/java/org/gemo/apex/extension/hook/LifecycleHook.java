package org.gemo.apex.extension.hook;

import org.gemo.apex.common.hook.HookTypeDescriptor;
import org.gemo.apex.common.hook.context.HookContextView;
import org.gemo.apex.common.hook.result.LifecycleHookResult;

public interface LifecycleHook<C extends HookContextView, R extends LifecycleHookResult> {
    String name();

    HookTypeDescriptor descriptor();

    R apply(C context);
}
