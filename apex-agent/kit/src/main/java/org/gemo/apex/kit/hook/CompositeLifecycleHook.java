package org.gemo.apex.kit.hook;

import java.util.List;
import org.gemo.apex.common.hook.HookTypeDescriptor;
import org.gemo.apex.common.hook.context.HookContextView;
import org.gemo.apex.common.hook.result.ContinueAgentBuild;
import org.gemo.apex.common.hook.result.ContinueLoop;
import org.gemo.apex.common.hook.result.ContinuePostMessageCompression;
import org.gemo.apex.common.hook.result.ContinuePostModelCall;
import org.gemo.apex.common.hook.result.ContinuePostToolCall;
import org.gemo.apex.common.hook.result.ContinuePreMessageCompression;
import org.gemo.apex.common.hook.result.ContinuePreModelCall;
import org.gemo.apex.common.hook.result.ContinuePreToolCall;
import org.gemo.apex.common.hook.result.ContinueTurnEnd;
import org.gemo.apex.common.hook.result.LifecycleHookResult;
import org.gemo.apex.extension.hook.LifecycleHook;

public final class CompositeLifecycleHook<C extends HookContextView, R extends LifecycleHookResult>
        implements LifecycleHook<C, R> {
    private final HookTypeDescriptor descriptor;
    private final List<LifecycleHook<C, R>> hooks;

    public CompositeLifecycleHook(List<LifecycleHook<C, R>> hooks) {
        if (hooks == null || hooks.isEmpty()) {
            throw new IllegalArgumentException("hooks 不能为空");
        }
        this.hooks = List.copyOf(hooks);
        this.descriptor = this.hooks.getFirst().descriptor();
        if (this.hooks.stream().anyMatch(hook -> !descriptor.equals(hook.descriptor()))) {
            throw new IllegalArgumentException("组合器中的 Hook descriptor 必须一致");
        }
    }

    @Override
    public HookTypeDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public R apply(C context) {
        R result = null;
        for (LifecycleHook<C, R> hook : hooks) {
            result = hook.apply(context);
            if (!continues(result)) {
                return result;
            }
        }
        return result;
    }

    private boolean continues(LifecycleHookResult result) {
        return result instanceof ContinueAgentBuild
                || result instanceof ContinueLoop
                || result instanceof ContinuePreMessageCompression
                || result instanceof ContinuePostMessageCompression
                || result instanceof ContinuePreModelCall
                || result instanceof ContinuePostModelCall
                || result instanceof ContinuePreToolCall
                || result instanceof ContinuePostToolCall
                || result instanceof ContinueTurnEnd;
    }
}
