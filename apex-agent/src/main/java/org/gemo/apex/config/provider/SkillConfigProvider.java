package org.gemo.apex.config.provider;

import org.gemo.apex.config.model.SkillConfig;
import java.util.List;

/**
 * Provider interface for Custom Skill configurations.
 * Allows decoupled configuration sources for Skills.
 */
public interface SkillConfigProvider {

    /**
     * Get the configuration for a specific Skill.
     * 
     * @param skillKey the unique identifier for the skill
     * @return the configuration, or null if not found
     */
    SkillConfig getSkillConfig(String skillKey);

    /**
     * Get a list of all configured skills.
     * 
     * @return list of skill configurations
     */
    List<SkillConfig> getAllSkills();
}
