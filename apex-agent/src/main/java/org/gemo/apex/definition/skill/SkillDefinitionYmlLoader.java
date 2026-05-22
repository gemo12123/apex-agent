package org.gemo.apex.definition.skill;

import org.gemo.apex.config.ApexGlobalProperties;
import org.gemo.apex.config.model.SkillConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SkillDefinitionYmlLoader implements ISkillDefinitionLoader {

    private final ApexGlobalProperties apexGlobalProperties;

    public SkillDefinitionYmlLoader(ApexGlobalProperties apexGlobalProperties) {
        this.apexGlobalProperties = apexGlobalProperties;
    }

    @Override
    public SkillConfig load(String skillKey) {
        Map<String, SkillConfig> skills = apexGlobalProperties.getSkills();
        return skills == null ? null : skills.get(skillKey);
    }
}
