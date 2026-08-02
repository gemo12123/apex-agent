package org.gemo.apex.core.skill;

import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.core.agent.ApexAgentContext;
import org.gemo.apex.core.exception.ToolContractException;

import java.util.Map;

public final class SkillActivationCoordinator {
    public static final String TOOL_NAME = "activate_skill";
    public static final String SKILL_NAME_ARGUMENT = "command";

    public ToolResult activate(ApexAgentContext context, ToolCall call) {
        Object rawName = call.arguments().get(SKILL_NAME_ARGUMENT);
        if (!(rawName instanceof String skillName) || skillName.isBlank()) {
            throw new ToolContractException("activate_skill.command 不能为空");
        }
        var activation = context.ports().skillActivator().activate(skillName,
                context.definition().definition().enabledSkills(), context.snapshot().activatedSkills());
        context.stageSkillActivation(activation);
        return new ToolResult(call.toolCallId(), call.name(), activation.instructions(), Map.of());
    }
}
