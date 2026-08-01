package org.gemo.apex.extension.hook;

import org.gemo.apex.common.hook.HookPoint;

public interface HookResolver {
    /**
     * 按生命周期点和稳定注册名解析；不使用 Bean 名语义，也不负责排序。
     */
    LifecycleHook<?, ?> resolve(HookPoint point, String name);
}
