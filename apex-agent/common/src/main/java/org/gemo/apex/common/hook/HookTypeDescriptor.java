package org.gemo.apex.common.hook;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.hook.context.HookContextView;
import org.gemo.apex.common.hook.result.LifecycleHookResult;

public record HookTypeDescriptor(
        HookPoint hookPoint,
        Class<? extends HookContextView> contextType,
        Class<? extends LifecycleHookResult> resultType) {
    public HookTypeDescriptor {
        hookPoint = nonNull(hookPoint, "hookPoint");
        contextType = nonNull(contextType, "contextType");
        resultType = nonNull(resultType, "resultType");
    }
}
