package org.gemo.apex.definition.skill;

import org.gemo.apex.config.model.SkillConfig;

public interface ISkillDefinitionLoader {

    SkillConfig load(String skillKey);
}
