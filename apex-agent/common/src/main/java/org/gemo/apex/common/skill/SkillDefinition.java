package org.gemo.apex.common.skill;

import static java.util.Collections.unmodifiableMap;
import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import java.util.LinkedHashMap;
import java.util.Map;

public record SkillDefinition(
        String name,
        String description,
        String instructions,
        Map<String, SkillResourceDescriptor> resources) {
    public SkillDefinition {
        name = required(name, "name");
        description = required(description, "description");
        instructions = required(instructions, "instructions");
        nonNull(resources, "resources");
        resources = unmodifiableMap(new LinkedHashMap<>(resources));
    }
}
