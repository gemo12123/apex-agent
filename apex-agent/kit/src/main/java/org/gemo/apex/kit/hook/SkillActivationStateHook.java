package org.gemo.apex.kit.hook;

import java.util.Map;
import java.util.Set;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.HookTypeDescriptor;
import org.gemo.apex.common.hook.context.PostToolCallContext;
import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.SkillActivationDelta;
import org.gemo.apex.common.hook.operation.ToolResultPatch;
import org.gemo.apex.common.hook.result.ContinuePostToolCall;
import org.gemo.apex.common.hook.result.PostToolCallHookResult;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.kit.tool.ActivateSkillTool;

public final class SkillActivationStateHook
        implements LifecycleHook<PostToolCallContext, PostToolCallHookResult> {
    public static final String REGISTRATION_NAME = "skillActivationStateHook";
    private static final HookTypeDescriptor DESCRIPTOR =
            new HookTypeDescriptor(
                    HookPoint.POST_TOOL_CALL,
                    PostToolCallContext.class,
                    PostToolCallHookResult.class);

    @Override
    public String name() {
        return REGISTRATION_NAME;
    }

    @Override
    public HookTypeDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public PostToolCallHookResult apply(PostToolCallContext context) {
        Object rawName =
                context.toolResult().metadata().get(ActivateSkillTool.ACTIVATED_SKILL_METADATA);
        SkillActivationDelta delta = SkillActivationDelta.none();
        if (rawName != null) {
            if (!(rawName instanceof String skillName) || skillName.isBlank()) {
                throw new IllegalArgumentException("activatedSkill metadata 必须是非空字符串");
            }
            delta = new SkillActivationDelta(Set.of(skillName), Set.of());
        }
        return new ContinuePostToolCall(
                HookMutations.none(),
                new ToolResultPatch(
                        context.toolResult().content(),
                        Map.copyOf(context.toolResult().metadata())),
                delta);
    }
}
