package org.gemo.apex.skills.learning;

import org.gemo.apex.skills.learning.model.SkillExperienceMemory;
import org.gemo.apex.skills.learning.model.SkillUsageRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillLearningRepositoryTest {

    @Test
    void inMemoryUsageRepositoryShouldKeepDuplicatesAndGroupCounts() {
        InMemorySkillUsageRecordRepository repository = new InMemorySkillUsageRecordRepository();

        repository.insert(SkillUsageRecord.builder()
                .id("u1")
                .agentKey("default_agent")
                .skillName("writing-plans")
                .sessionId("session-1")
                .turnNo(3)
                .activationMessageSortNo(11L)
                .build());
        repository.insert(SkillUsageRecord.builder()
                .id("u2")
                .agentKey("default_agent")
                .skillName("writing-plans")
                .sessionId("session-1")
                .turnNo(4)
                .activationMessageSortNo(21L)
                .build());

        assertEquals(2, repository.countByAgentAndSkill().get("default_agent::writing-plans"));
        assertEquals(2, repository.findByAgentAndSkill("default_agent", "writing-plans").size());
    }

    @Test
    void inMemoryExperienceRepositoryShouldOverwriteContentAndIncrementVersion() {
        InMemorySkillExperienceMemoryRepository repository = new InMemorySkillExperienceMemoryRepository();

        SkillExperienceMemory first = repository.upsert("default_agent", "writing-plans", "old");
        SkillExperienceMemory second = repository.upsert("default_agent", "writing-plans", "new");

        assertEquals(1L, first.getVersionNo());
        assertEquals(2L, second.getVersionNo());
        assertEquals("new", repository.find("default_agent", "writing-plans").orElseThrow().getContent());
    }

    @Test
    void propertiesShouldExposeSpecDefaults() {
        SkillExperienceLearningProperties properties = new SkillExperienceLearningProperties();

        assertTrue(properties.isEnabled());
        assertEquals(5, properties.getUsageThreshold());
        assertEquals("0 0 4 * * *", properties.getDailyCron());
        assertEquals("Skill经验", properties.getExperienceSectionTitle());
    }
}
