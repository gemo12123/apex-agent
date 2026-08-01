package org.gemo.apex.config.model;

import lombok.Data;

/**
 * Global configuration model for a Custom Skill
 */
@Data
public class SkillConfig {

    /** The absolute or classpath directory containing the skill markdown */
    private String dir;
}
