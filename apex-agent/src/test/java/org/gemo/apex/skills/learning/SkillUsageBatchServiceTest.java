package org.gemo.apex.skills.learning;

import org.gemo.apex.skills.learning.model.SkillConversationSlice;
import org.gemo.apex.skills.learning.model.SkillExperienceMemory;
import org.gemo.apex.skills.learning.model.SkillSessionMessage;
import org.gemo.apex.skills.learning.model.SkillUsageRecord;
import org.gemo.apex.skills.learning.model.SkillUsageValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillUsageBatchServiceTest {

    @Mock
    private SkillUsageRecordRepository usageRepository;

    @Mock
    private SkillExperienceMemoryRepository experienceRepository;

    @Mock
    private SkillUsageMessageCollector messageCollector;

    @Mock
    private SkillExperienceExtractor extractor;

    private SkillUsageBatchService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SkillExperienceLearningProperties properties = new SkillExperienceLearningProperties();
        properties.setUsageThreshold(2);
        service = new SkillUsageBatchService(properties, usageRepository, experienceRepository, messageCollector, extractor);
    }

    @Test
    void processEligibleGroupShouldDeleteInvalidRowsBeforeThresholdCheck() {
        SkillUsageRecord first = record("u1", 10L);
        SkillUsageRecord second = record("u2", 20L);
        SkillUsageRecord third = record("u3", 30L);
        when(usageRepository.findByAgentAndSkill("default_agent", "writing-plans")).thenReturn(List.of(first, second, third));
        when(messageCollector.validate(first)).thenReturn(SkillUsageValidationResult.valid(first));
        when(messageCollector.validate(second)).thenReturn(SkillUsageValidationResult.invalid(second, "bad"));
        when(messageCollector.validate(third)).thenReturn(SkillUsageValidationResult.valid(third));
        when(messageCollector.collectValidSlices(List.of(first, third))).thenReturn(List.of());

        service.processGroup("default_agent", "writing-plans", 3);

        verify(usageRepository).deleteByIds(List.of("u2"));
        verifyNoInteractions(experienceRepository);
        verifyNoInteractions(extractor);
    }

    @Test
    void processEligibleGroupShouldUpsertExperienceAndDeleteParticipatingRows() {
        SkillUsageRecord first = record("u1", 10L);
        SkillUsageRecord second = record("u2", 20L);
        List<SkillConversationSlice> slices = List.of(slice("session-1", 10L), slice("session-2", 20L));

        when(usageRepository.findByAgentAndSkill("default_agent", "writing-plans")).thenReturn(List.of(first, second));
        when(messageCollector.validate(any())).thenAnswer(invocation -> SkillUsageValidationResult.valid(invocation.getArgument(0)));
        when(messageCollector.collectValidSlices(List.of(first, second))).thenReturn(slices);
        when(experienceRepository.find("default_agent", "writing-plans")).thenReturn(Optional.of(
                SkillExperienceMemory.builder()
                        .agentKey("default_agent")
                        .skillName("writing-plans")
                        .content("old")
                        .versionNo(1L)
                        .build()));
        when(extractor.regenerate("default_agent", "writing-plans", "old", slices)).thenReturn("new");

        service.processGroup("default_agent", "writing-plans", 2);

        verify(experienceRepository).upsert("default_agent", "writing-plans", "new");
        verify(usageRepository).deleteByIds(List.of("u1", "u2"));
    }

    @Test
    void failedExtractionShouldKeepValidUsageRowsForRetry() {
        SkillUsageRecord first = record("u1", 10L);
        SkillUsageRecord second = record("u2", 20L);
        List<SkillConversationSlice> slices = List.of(slice("session-1", 10L), slice("session-2", 20L));

        when(usageRepository.findByAgentAndSkill("default_agent", "writing-plans")).thenReturn(List.of(first, second));
        when(messageCollector.validate(any())).thenAnswer(invocation -> SkillUsageValidationResult.valid(invocation.getArgument(0)));
        when(messageCollector.collectValidSlices(List.of(first, second))).thenReturn(slices);
        when(experienceRepository.find("default_agent", "writing-plans")).thenReturn(Optional.empty());
        when(extractor.regenerate("default_agent", "writing-plans", "", slices))
                .thenThrow(new IllegalStateException("llm down"));

        assertThrows(IllegalStateException.class,
                () -> service.processGroup("default_agent", "writing-plans", 2));

        verify(usageRepository, never()).deleteByIds(List.of("u1", "u2"));
    }

    private SkillUsageRecord record(String id, long sortNo) {
        return SkillUsageRecord.builder()
                .id(id)
                .agentKey("default_agent")
                .skillName("writing-plans")
                .sessionId("session-" + id)
                .activationMessageSortNo(sortNo)
                .build();
    }

    private SkillConversationSlice slice(String sessionId, long sortNo) {
        return new SkillConversationSlice(sessionId, sortNo, List.of(
                new SkillSessionMessage(sortNo, "user", null, null, "Need a plan")));
    }
}
