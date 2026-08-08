package org.gemo.apex.extension.skill;

import java.util.Set;
import org.gemo.apex.common.skill.SkillActivationResult;

public interface SkillActivator {
    SkillActivationResult activate(
            String skillName, Set<String> enabledSkills, Set<String> activatedSkills);
}
