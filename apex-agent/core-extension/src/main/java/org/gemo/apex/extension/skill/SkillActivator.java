package org.gemo.apex.extension.skill;

import org.gemo.apex.common.skill.SkillActivationResult;

import java.util.Set;

public interface SkillActivator {
    SkillActivationResult activate(String skillName, Set<String> enabledSkills,
                                   Set<String> activatedSkills);
}
