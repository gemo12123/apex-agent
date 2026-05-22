package org.gemo.apex.definition.skill;

import org.gemo.apex.config.ApexGlobalProperties;
import org.gemo.apex.config.model.SkillConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkillDefinitionYmlLoaderTest {

    @Test
    void loadShouldReturnConfiguredSkillDefinition() {
        ApexGlobalProperties properties = new ApexGlobalProperties();
        SkillConfig config = new SkillConfig();
        config.setDir("/tmp/skills/meeting");
        properties.setSkills(Map.of("meeting-skill", config));

        ISkillDefinitionLoader loader = new SkillDefinitionYmlLoader(properties);

        SkillConfig loaded = loader.load("meeting-skill");

        assertEquals("/tmp/skills/meeting", loaded.getDir());
    }

    @Test
    void loadShouldReturnNullForUnknownSkill() {
        ISkillDefinitionLoader loader = new SkillDefinitionYmlLoader(new ApexGlobalProperties());

        assertNull(loader.load("missing"));
    }
}
