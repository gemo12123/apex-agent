package org.gemo.apex.kit.hook;

import java.util.Objects;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.HookTypeDescriptor;
import org.gemo.apex.common.hook.context.PreToolCallContext;
import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ToolCallPatch;
import org.gemo.apex.common.hook.result.BlockTool;
import org.gemo.apex.common.hook.result.ContinuePreToolCall;
import org.gemo.apex.common.hook.result.PreToolCallHookResult;
import org.gemo.apex.common.hook.result.RequestHumanIntervention;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.kit.intervention.QuestionInterventionFactory;
import org.gemo.apex.kit.tool.AskHumanTool;

public final class AskHumanInterventionHook
        implements LifecycleHook<PreToolCallContext, PreToolCallHookResult> {
    public static final String REGISTRATION_NAME = "askHumanInterventionHook";
    private static final HookTypeDescriptor DESCRIPTOR =
            new HookTypeDescriptor(
                    HookPoint.PRE_TOOL_CALL, PreToolCallContext.class, PreToolCallHookResult.class);
    private final QuestionInterventionFactory factory;

    public AskHumanInterventionHook() {
        this(new QuestionInterventionFactory());
    }

    public AskHumanInterventionHook(QuestionInterventionFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    @Override
    public HookTypeDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public PreToolCallHookResult apply(PreToolCallContext context) {
        if (!AskHumanTool.NAME.equals(context.toolCall().name())
                || context.humanSubmission() != null) {
            return new ContinuePreToolCall(
                    HookMutations.none(), new ToolCallPatch(context.toolCall().arguments()));
        }
        try {
            return new RequestHumanIntervention(factory.create(context.toolCall()));
        } catch (IllegalArgumentException exception) {
            return new BlockTool(exception.getMessage());
        }
    }
}
