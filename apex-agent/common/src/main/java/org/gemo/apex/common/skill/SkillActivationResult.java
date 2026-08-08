package org.gemo.apex.common.skill;

import static org.gemo.apex.common.support.DomainValues.immutableNames;
import static org.gemo.apex.common.support.DomainValues.required;

import java.util.Set;

public record SkillActivationResult(String instructions, Set<String> activatedSkills) {
    public SkillActivationResult {
        instructions = required(instructions, "instructions");
        activatedSkills = immutableNames(activatedSkills, "activatedSkills");
    }
}
