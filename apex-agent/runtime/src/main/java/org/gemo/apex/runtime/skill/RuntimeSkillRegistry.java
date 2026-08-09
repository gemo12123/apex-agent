package org.gemo.apex.runtime.skill;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.gemo.apex.common.skill.*;
import org.gemo.apex.extension.skill.*;

public final class RuntimeSkillRegistry implements SkillProvider {
    private final Map<String, SkillDefinition> skills;

    public RuntimeSkillRegistry(List<SkillDefinition> in) {
        Map<String, SkillDefinition> m = new LinkedHashMap<>();
        for (var s : List.copyOf(in)) {
            if (m.putIfAbsent(s.name(), s) != null) {
                throw new IllegalArgumentException("Skill 重名: " + s.name());
            }
        }
        skills = Map.copyOf(m);
    }

    public List<SkillDefinition> loadSkills() {
        return List.copyOf(skills.values());
    }

    public String read(String skill, String resource, Set<String> enabled) {
        if (!enabled.contains(skill)) {
            throw new IllegalArgumentException("Skill 未启用: " + skill);
        }
        var s = Optional.ofNullable(skills.get(skill)).orElseThrow();
        var d = Optional.ofNullable(s.resources().get(resource)).orElseThrow();
        try {
            return Files.readString(Path.of(URI.create(d.location())));
        } catch (IOException e) {
            throw new IllegalStateException("读取 Skill 资源失败", e);
        }
    }
}
