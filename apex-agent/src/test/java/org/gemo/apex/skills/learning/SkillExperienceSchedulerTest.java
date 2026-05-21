package org.gemo.apex.skills.learning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillExperienceSchedulerTest {

    @Mock
    private SkillUsageRecordRepository usageRepository;

    @Mock
    private SkillUsageBatchService batchService;

    private SkillExperienceScheduler scheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SkillExperienceLearningProperties properties = new SkillExperienceLearningProperties();
        properties.setUsageThreshold(5);
        scheduler = new SkillExperienceScheduler(properties, usageRepository, batchService);
    }

    @Test
    void schedulerShouldOnlyProcessGroupsMeetingThreshold() {
        when(usageRepository.countByAgentAndSkill()).thenReturn(Map.of(
                "default_agent::writing-plans", 5,
                "default_agent::brainstorming", 2));

        scheduler.runDailyScan();

        verify(batchService).processGroup("default_agent", "writing-plans", 5);
        verify(batchService, never()).processGroup("default_agent", "brainstorming", 2);
    }

    @Test
    void schedulerShouldContinueWhenOneGroupProcessingFails() {
        when(usageRepository.countByAgentAndSkill()).thenReturn(Map.of(
                "default_agent::writing-plans", 5,
                "default_agent::brainstorming", 6));
        doThrow(new IllegalStateException("llm down"))
                .when(batchService).processGroup("default_agent", "writing-plans", 5);

        scheduler.runDailyScan();

        verify(batchService).processGroup("default_agent", "writing-plans", 5);
        verify(batchService).processGroup("default_agent", "brainstorming", 6);
    }
}
