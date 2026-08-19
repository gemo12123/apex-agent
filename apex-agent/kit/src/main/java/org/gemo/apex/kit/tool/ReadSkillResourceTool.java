package org.gemo.apex.kit.tool;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolDefinition;
import org.gemo.apex.common.tool.ToolExecutionContext;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.extension.skill.SkillProvider;
import org.gemo.apex.extension.tool.AgentTool;
import org.gemo.apex.extension.tool.ToolExecutionObserver;

public final class ReadSkillResourceTool implements AgentTool {
    public static final String NAME = "read_skill_resource";
    private final SkillProvider skills;
    private final Set<String> enabled;

    public ReadSkillResourceTool(SkillProvider s, Set<String> e) {
        skills = s;
        enabled = Set.copyOf(e);
    }

    public ToolDefinition definition() {
        return new ToolDefinition(
                NAME,
                "读取已启用 Skill 的资源",
                "{\"type\":\"object\",\"properties\":{\"skillName\":{\"type\":\"string\"},\"path\":{\"type\":\"string\"}},\"required\":[\"skillName\",\"path\"]}",
                Map.of());
    }

    public ToolResult execute(ToolCall c, ToolExecutionContext x, ToolExecutionObserver o) {
        String skill = Objects.toString(c.arguments().get("skillName"), "");
        String path = Objects.toString(c.arguments().get("path"), "");
        if (!enabled.contains(skill)) {
            throw new IllegalArgumentException("Skill 未启用: " + skill);
        }
        return new ToolResult(c.toolCallId(), c.name(), skills.loadResource(skill, path), Map.of());
    }
}
