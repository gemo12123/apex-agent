package org.gemo.apex.common.skill;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

public record SkillDefinition(SkillMeta meta, String instructions) {
    public SkillDefinition {
        nonNull(meta, "meta");
        instructions = required(instructions, "instructions");
    }
}
