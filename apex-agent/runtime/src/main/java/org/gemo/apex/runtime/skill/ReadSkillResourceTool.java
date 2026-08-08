package org.gemo.apex.runtime.skill;

import org.gemo.apex.common.tool.*;
import org.gemo.apex.extension.tool.*;

import java.util.*;

public final class ReadSkillResourceTool implements AgentTool {
    public static final String NAME = "read_skill_resource";
    private final RuntimeSkillRegistry skills;
    private final Set<String> enabled;

    public ReadSkillResourceTool(RuntimeSkillRegistry s, Set<String> e) {
        skills = s;
        enabled = Set.copyOf(e);
    }

    public ToolDefinition definition() {
        return new ToolDefinition(NAME, "读取已启用 Skill 的资源", "{\"type\":\"object\"}", Map.of());
    }

    public ToolResult execute(ToolCall c, ToolExecutionContext x, ToolExecutionObserver o) {
        String skill = Objects.toString(c.arguments().get("skillName"), "");
        String path = Objects.toString(c.arguments().get("path"), "");
        return new ToolResult(c.toolCallId(), c.name(), skills.read(skill, path, enabled), Map.of());
    }
}
