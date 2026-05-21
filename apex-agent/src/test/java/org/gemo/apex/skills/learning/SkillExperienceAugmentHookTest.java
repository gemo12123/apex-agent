package org.gemo.apex.skills.learning;

import org.gemo.apex.hook.tool.PostToolCallHookContext;
import org.gemo.apex.hook.tool.PostToolCallHookResult;
import org.gemo.apex.skills.learning.model.SkillExperienceMemory;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SkillExperienceAugmentHookTest {

    @Test
    void augmentHookShouldPreserveActivatedSkillWrapper() {
        SkillExperienceLearningProperties properties = new SkillExperienceLearningProperties();
        SkillExperienceMemoryRepository repository = mock(SkillExperienceMemoryRepository.class);
        when(repository.find("default_agent", "writing-plans")).thenReturn(Optional.of(
                SkillExperienceMemory.builder()
                        .agentKey("default_agent")
                        .skillName("writing-plans")
                        .content("Remember to keep tasks bite-sized.")
                        .versionNo(1L)
                        .build()));
        SkillExperienceAugmentHook hook = new SkillExperienceAugmentHook(properties, repository);

        PostToolCallHookResult result = hook.apply(PostToolCallHookContext.builder()
                .agentKey("default_agent")
                .toolName("activate_skill")
                .arguments(Map.of("command", "writing-plans"))
                .currentResult("""
                        <activated_skill name="writing-plans">
                          <instructions>
                            body
                          </instructions>
                        </activated_skill>
                        """)
                .build());

        assertEquals(PostToolCallHookResult.Outcome.REPLACE_RESULT, result.getOutcome());
        assertTrue(result.getNextResult().contains("<activated_skill name=\"writing-plans\">"));
        assertTrue(result.getNextResult().contains("# Skill经验"));
    }

    @Test
    void augmentHookShouldSkipWhenLearningDisabled() {
        SkillExperienceLearningProperties properties = new SkillExperienceLearningProperties();
        properties.setEnabled(false);
        SkillExperienceMemoryRepository repository = mock(SkillExperienceMemoryRepository.class);
        SkillExperienceAugmentHook hook = new SkillExperienceAugmentHook(properties, repository);

        PostToolCallHookResult result = hook.apply(PostToolCallHookContext.builder()
                .agentKey("default_agent")
                .toolName("activate_skill")
                .arguments(Map.of("command", "writing-plans"))
                .currentResult("<activated_skill name=\"writing-plans\"><instructions>body</instructions></activated_skill>")
                .build());

        assertSame(PostToolCallHookResult.Outcome.KEEP, result.getOutcome());
        verifyNoInteractions(repository);
    }
}
