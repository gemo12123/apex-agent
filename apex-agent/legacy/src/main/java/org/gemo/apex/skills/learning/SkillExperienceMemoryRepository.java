package org.gemo.apex.skills.learning;

import org.gemo.apex.skills.learning.model.SkillExperienceMemory;

import java.util.Optional;

public interface SkillExperienceMemoryRepository {

    Optional<SkillExperienceMemory> find(String agentKey, String skillName);

    SkillExperienceMemory upsert(String agentKey, String skillName, String content);
}
