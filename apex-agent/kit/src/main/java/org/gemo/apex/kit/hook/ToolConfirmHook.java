package org.gemo.apex.kit.hook;

import java.util.Objects;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.HookTypeDescriptor;
import org.gemo.apex.common.hook.context.PreToolCallContext;
import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ToolCallPatch;
import org.gemo.apex.common.hook.result.ContinuePreToolCall;
import org.gemo.apex.common.hook.result.PreToolCallHookResult;
import org.gemo.apex.common.hook.result.RequestHumanIntervention;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.kit.intervention.ToolConfirmationSpecFactory;

public final class ToolConfirmHook
        implements LifecycleHook<PreToolCallContext, PreToolCallHookResult> {
    public static final String REGISTRATION_NAME = "toolConfirmHook";
    private static final HookTypeDescriptor DESCRIPTOR =
            new HookTypeDescriptor(
                    HookPoint.PRE_TOOL_CALL, PreToolCallContext.class, PreToolCallHookResult.class);
    private final ToolConfirmationSpecFactory factory;

    public ToolConfirmHook() {
        this(new ToolConfirmationSpecFactory());
    }

    public ToolConfirmHook(ToolConfirmationSpecFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    @Override
    public String name() {
        return REGISTRATION_NAME;
    }

    @Override
    public HookTypeDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public PreToolCallHookResult apply(PreToolCallContext context) {
        if (context.humanSubmission() != null) {
            return new ContinuePreToolCall(
                    HookMutations.none(), new ToolCallPatch(context.toolCall().arguments()));
        }
        return new RequestHumanIntervention(factory.create(context));
    }
}
