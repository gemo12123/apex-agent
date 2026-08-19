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
import org.gemo.apex.common.intervention.QuestionInterventionRequest;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.kit.intervention.AskHumanArgumentsParser;
import org.gemo.apex.kit.tool.AskHumanTool;

public final class AskHumanInterventionHook
        implements LifecycleHook<PreToolCallContext, PreToolCallHookResult> {
    public static final String REGISTRATION_NAME = "askHumanInterventionHook";
    private static final HookTypeDescriptor DESCRIPTOR =
            new HookTypeDescriptor(
                    HookPoint.PRE_TOOL_CALL, PreToolCallContext.class, PreToolCallHookResult.class);
    private final AskHumanArgumentsParser parser;

    public AskHumanInterventionHook() {
        this(new AskHumanArgumentsParser());
    }

    public AskHumanInterventionHook(AskHumanArgumentsParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
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
        if (!AskHumanTool.NAME.equals(context.toolCall().name())
                || context.humanSubmission() != null) {
            return new ContinuePreToolCall(
                    HookMutations.none(), new ToolCallPatch(context.toolCall().arguments()));
        }
        try {
            return new RequestHumanIntervention(
                    new QuestionInterventionRequest(
                            context.toolCall().toolCallId(),
                            parser.parse(context.toolCall().arguments())));
        } catch (IllegalArgumentException exception) {
            return new BlockTool(exception.getMessage());
        }
    }
}
