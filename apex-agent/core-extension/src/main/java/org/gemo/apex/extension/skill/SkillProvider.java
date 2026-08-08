package org.gemo.apex.extension.skill;

import java.util.List;
import org.gemo.apex.common.skill.SkillDefinition;

public interface SkillProvider {
    List<SkillDefinition> loadSkills();
}
