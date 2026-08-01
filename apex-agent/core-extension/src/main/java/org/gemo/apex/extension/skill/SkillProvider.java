package org.gemo.apex.extension.skill;

import org.gemo.apex.common.skill.SkillDefinition;

import java.util.List;

public interface SkillProvider {
    List<SkillDefinition> loadSkills();
}
