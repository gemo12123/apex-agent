package org.gemo.apex.skills.learning;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SkillExperienceScheduler {

    private final SkillExperienceLearningProperties properties;
    private final SkillUsageRecordRepository usageRepository;
    private final SkillUsageBatchService batchService;

    public SkillExperienceScheduler(SkillExperienceLearningProperties properties,
            SkillUsageRecordRepository usageRepository,
            SkillUsageBatchService batchService) {
        this.properties = properties;
        this.usageRepository = usageRepository;
        this.batchService = batchService;
    }

    @Scheduled(cron = "${apex.skills.learning.daily-cron:0 0 4 * * *}")
    public void runDailyScan() {
        if (!properties.isEnabled()) {
            return;
        }
        usageRepository.countByAgentAndSkill().forEach((groupKey, count) -> {
            if (count < properties.getUsageThreshold()) {
                return;
            }
            String[] parts = groupKey.split("::", 2);
            if (parts.length < 2) {
                log.warn("Skipping malformed skill learning group key: {}", groupKey);
                return;
            }
            batchService.processGroup(parts[0], parts[1], count);
        });
    }
}
