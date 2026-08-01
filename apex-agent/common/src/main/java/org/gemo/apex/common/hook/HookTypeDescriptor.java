package org.gemo.apex.common.hook;

import org.gemo.apex.common.hook.context.HookContextView;
import org.gemo.apex.common.hook.result.LifecycleHookResult;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record HookTypeDescriptor(HookPoint hookPoint, Class<? extends HookContextView> contextType,
                                 Class<? extends LifecycleHookResult> resultType) {
    public HookTypeDescriptor {
        hookPoint = nonNull(hookPoint, "hookPoint");
        contextType = nonNull(contextType, "contextType");
        resultType = nonNull(resultType, "resultType");
    }
}
