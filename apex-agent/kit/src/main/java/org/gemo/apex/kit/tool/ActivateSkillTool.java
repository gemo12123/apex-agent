package org.gemo.apex.kit.tool;

import java.util.Map;
import java.util.Objects;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolDefinition;
import org.gemo.apex.common.tool.ToolExecutionContext;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.extension.skill.SkillProvider;
import org.gemo.apex.extension.tool.AgentTool;
import org.gemo.apex.extension.tool.ToolExecutionObserver;

public final class ActivateSkillTool implements AgentTool {
    public static final String NAME = "activate_skill";
    public static final String COMMAND_ARGUMENT = "command";
    public static final String ACTIVATED_SKILL_METADATA = "activatedSkill";
    private static final ToolDefinition DEFINITION =
            new ToolDefinition(
                    NAME,
                    "激活一个 Skill",
                    "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"}},\"required\":[\"command\"]}",
                    Map.of());

    private final SkillProvider skills;

    public ActivateSkillTool(SkillProvider skills) {
        this.skills = Objects.requireNonNull(skills, "skills");
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(
            ToolCall call, ToolExecutionContext context, ToolExecutionObserver observer) {
        if (!NAME.equals(call.name())) {
            throw new IllegalArgumentException("ActivateSkillTool 只能执行 activate_skill 调用");
        }
        Object rawName = call.arguments().get(COMMAND_ARGUMENT);
        if (!(rawName instanceof String skillName) || skillName.isBlank()) {
            throw new IllegalArgumentException("activate_skill.command 不能为空");
        }
        if (!context.enabledSkills().contains(skillName)) {
            throw new IllegalArgumentException("Skill 未启用: " + skillName);
        }
        SkillDefinition skill =
                skills.loadSkills().stream()
                        .filter(candidate -> candidate.name().equals(skillName))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Skill 不存在: " + skillName));
        return new ToolResult(
                call.toolCallId(),
                call.name(),
                skill.instructions(),
                Map.of(ACTIVATED_SKILL_METADATA, skillName));
    }
}
