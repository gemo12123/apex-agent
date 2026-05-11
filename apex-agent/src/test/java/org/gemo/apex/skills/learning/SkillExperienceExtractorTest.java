package org.gemo.apex.skills.learning;

import org.gemo.apex.memory.extract.PromptTemplateLoader;
import org.gemo.apex.skills.learning.model.SkillConversationSlice;
import org.gemo.apex.skills.learning.model.SkillSessionMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillExperienceExtractorTest {

    private PromptTemplateLoader promptTemplateLoader;
    private SkillExperienceLearningProperties properties;

    @BeforeEach
    void setUp() {
        promptTemplateLoader = mock(PromptTemplateLoader.class);
        properties = new SkillExperienceLearningProperties();
    }

    @Test
    void buildPromptShouldIncludeExistingExperienceAndConversationSlices() {
        SkillExperiencePromptService service = new SkillExperiencePromptService(properties, promptTemplateLoader);
        when(promptTemplateLoader.load("classpath:prompts/skills/skill-experience-learning.st"))
                .thenReturn("agent={agentKey}\nskill={skillName}\nold={existingExperience}\nconv={conversationSlices}");

        String prompt = service.buildPrompt("default_agent", "writing-plans", "old-exp", List.of(
                new SkillConversationSlice("session-1", 10L, List.of(
                        new SkillSessionMessage(9L, "user", null, null, "Need a plan")))));

        assertTrue(prompt.contains("agent=default_agent"));
        assertTrue(prompt.contains("skill=writing-plans"));
        assertTrue(prompt.contains("old=old-exp"));
        assertTrue(prompt.contains("session-1"));
    }
}
