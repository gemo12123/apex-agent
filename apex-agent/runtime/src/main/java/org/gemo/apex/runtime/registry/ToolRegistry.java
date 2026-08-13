package org.gemo.apex.runtime.registry;

import java.util.*;
import org.gemo.apex.common.agent.*;
import org.gemo.apex.extension.tool.*;
import org.gemo.apex.kit.tool.ReadSkillResourceTool;
import org.gemo.apex.runtime.skill.*;

public final class ToolRegistry implements ToolProvider {
    private final Map<String, AgentTool> tools;
    private final RuntimeSkillRegistry skills;

    public ToolRegistry(List<AgentTool> input, RuntimeSkillRegistry skills) {
        this.skills = skills;
        Map<String, AgentTool> m = new LinkedHashMap<>();
        for (var t : List.copyOf(input)) {
            if (m.putIfAbsent(t.definition().name(), t) != null) {
                throw new IllegalArgumentException("工具重名: " + t.definition().name());
            }
        }
        tools = Map.copyOf(m);
    }

    private List<AgentTool> get(Set<String> names, Set<String> enabledSkills) {
        List<AgentTool> out = new ArrayList<>();
        for (String name : names) {
            if (ReadSkillResourceTool.NAME.equals(name)) {
                out.add(new ReadSkillResourceTool(skills, enabledSkills));
            } else if (tools.containsKey(name)) {
                out.add(tools.get(name));
            }
        }
        return List.copyOf(out);
    }

    public List<AgentTool> loadTools(AgentDefinition d) {
        return get(d.tools().availableTools(), d.enabledSkills());
    }
}
