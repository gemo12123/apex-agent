package org.gemo.apex.common.skill;

import static org.gemo.apex.common.support.DomainValues.immutableNames;

import java.util.Set;
import org.gemo.apex.common.exception.DomainInvariantException;

public record SkillSetDefinition(Set<String> availableSkills, Set<String> defaultEnabledSkills) {
    public SkillSetDefinition {
        availableSkills = immutableNames(availableSkills, "availableSkills");
        defaultEnabledSkills = immutableNames(defaultEnabledSkills, "defaultEnabledSkills");
        if (!availableSkills.containsAll(defaultEnabledSkills)) {
            throw new DomainInvariantException("defaultEnabledSkills 必须是 availableSkills 的子集");
        }
    }
}
