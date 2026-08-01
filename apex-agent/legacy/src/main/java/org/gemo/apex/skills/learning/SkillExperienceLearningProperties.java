package org.gemo.apex.skills.learning;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "apex.skills.learning")
public class SkillExperienceLearningProperties {

    private boolean enabled = true;
    private int usageThreshold = 5;
    private String dailyCron = "0 0 4 * * *";
    private int longSessionMessageThreshold = 40;
    private int activationWindowBefore = 8;
    private int activationWindowAfter = 12;
    private String experiencePrompt = "classpath:prompts/skills/skill-experience-learning.st";
    private String experienceSectionTitle = "Skill经验";
}
