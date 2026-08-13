package org.gemo.apex.runtime.skill;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.skill.SkillMeta;
import org.gemo.apex.extension.skill.SkillProvider;

/** 合并多个 SkillProvider，并将完整 Skill 与资源读取路由到声明该 Skill 的 Provider。 */
public final class RuntimeSkillRegistry implements SkillProvider {
    private final Map<String, Binding> skills;

    public RuntimeSkillRegistry(List<SkillProvider> providers) {
        Map<String, Binding> merged = new LinkedHashMap<>();
        for (SkillProvider provider : List.copyOf(providers)) {
            Objects.requireNonNull(provider, "skillProvider");
            List<SkillMeta> metadata =
                    List.copyOf(
                            Objects.requireNonNull(
                                    provider.loadSkills(), "SkillProvider.loadSkills 返回值不能为空"));
            Set<String> names = new HashSet<>();
            for (SkillMeta meta : metadata) {
                Objects.requireNonNull(meta, "skillMeta");
                if (!names.add(meta.name())) {
                    throw new IllegalArgumentException(
                            "同一 SkillProvider 内 Skill 重名: " + meta.name());
                }
                merged.put(meta.name(), new Binding(meta, provider));
            }
        }
        skills = Collections.unmodifiableMap(new LinkedHashMap<>(merged));
    }

    @Override
    public List<SkillMeta> loadSkills() {
        return skills.values().stream().map(Binding::meta).toList();
    }

    @Override
    public SkillDefinition loadSkill(String skillName) {
        SkillDefinition definition =
                Objects.requireNonNull(
                        binding(skillName).provider().loadSkill(skillName),
                        "SkillProvider.loadSkill 返回值不能为空");
        if (!skillName.equals(definition.meta().name())) {
            throw new IllegalStateException(
                    "SkillProvider 返回了错误的 Skill: " + definition.meta().name());
        }
        return definition;
    }

    @Override
    public String loadResource(String skillName, String resourcePath) {
        return binding(skillName).provider().loadResource(skillName, resourcePath);
    }

    @Override
    public String loadResource(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Skill 资源路径不能为空");
        }
        int separator = path.indexOf('/');
        if (separator <= 0 || separator == path.length() - 1) {
            throw new IllegalArgumentException("Skill 资源路径必须为 skillName/relativePath");
        }
        return loadResource(path.substring(0, separator), path.substring(separator + 1));
    }

    private Binding binding(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("skillName 不能为空");
        }
        Binding binding = skills.get(skillName);
        if (binding == null) {
            throw new IllegalArgumentException("Skill 不存在: " + skillName);
        }
        return binding;
    }

    private record Binding(SkillMeta meta, SkillProvider provider) {}
}
