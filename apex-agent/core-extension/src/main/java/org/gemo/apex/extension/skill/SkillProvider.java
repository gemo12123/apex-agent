package org.gemo.apex.extension.skill;

import java.util.List;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.skill.SkillMeta;

public interface SkillProvider {
    List<SkillMeta> loadSkills();

    SkillDefinition loadSkill(String skillName);

    String loadResource(String skillName, String resourcePath);

    String loadResource(String path);
}
