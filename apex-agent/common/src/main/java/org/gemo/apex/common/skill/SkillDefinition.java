package org.gemo.apex.common.skill;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 完整 Skill 定义。instructions 与资源 content 均允许由 Provider 按需填充一次。 */
public final class SkillDefinition {
    private final SkillMeta meta;
    private final Map<String, SkillResource> resources;
    private volatile String instructions;

    public SkillDefinition(SkillMeta meta, String instructions) {
        this(meta, instructions, Map.of());
    }

    public SkillDefinition(SkillMeta meta, Map<String, SkillResource> resources) {
        this(meta, null, resources);
    }

    public SkillDefinition(
            SkillMeta meta, String instructions, Map<String, SkillResource> resources) {
        this.meta = nonNull(meta, "meta");
        this.instructions = instructions == null ? null : required(instructions, "instructions");
        nonNull(resources, "resources");
        Map<String, SkillResource> copy = new LinkedHashMap<>();
        resources.forEach(
                (path, resource) -> {
                    String resourcePath = required(path, "resources path");
                    SkillResource value = nonNull(resource, "resource");
                    if (!resourcePath.equals(value.path())) {
                        throw new IllegalArgumentException("SkillResource path 与资源索引不一致: " + path);
                    }
                    copy.put(resourcePath, value);
                });
        this.resources = Collections.unmodifiableMap(copy);
    }

    public SkillMeta meta() {
        return meta;
    }

    public String instructions() {
        return instructions;
    }

    public Map<String, SkillResource> resources() {
        return resources;
    }

    public void cacheInstructions(String value) {
        String loaded = required(value, "instructions");
        synchronized (this) {
            if (instructions == null) {
                instructions = loaded;
            }
        }
    }
}
