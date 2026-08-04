package org.gemo.apex.skills.learning;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.skills.learning.model.SkillConversationSlice;
import org.gemo.apex.skills.learning.model.SkillExperienceMemory;
import org.gemo.apex.skills.learning.model.SkillUsageRecord;
import org.gemo.apex.skills.learning.model.SkillUsageValidationResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class SkillUsageBatchService {

    private final SkillExperienceLearningProperties properties;
    private final SkillUsageRecordRepository usageRepository;
    private final SkillExperienceMemoryRepository experienceRepository;
    private final SkillUsageMessageCollector messageCollector;
    private final SkillExperienceExtractor extractor;
    private final ConcurrentMap<String, ReentrantLock> groupLocks = new ConcurrentHashMap<>();

    public SkillUsageBatchService(SkillExperienceLearningProperties properties,
            SkillUsageRecordRepository usageRepository,
            SkillExperienceMemoryRepository experienceRepository,
            SkillUsageMessageCollector messageCollector,
            SkillExperienceExtractor extractor) {
        this.properties = properties;
        this.usageRepository = usageRepository;
        this.experienceRepository = experienceRepository;
        this.messageCollector = messageCollector;
        this.extractor = extractor;
    }

    @Transactional
    public void processGroup(String agentKey, String skillName, int groupedCount) {
        ReentrantLock lock = groupLocks.computeIfAbsent(agentKey + "::" + skillName, key -> new ReentrantLock());
        if (!lock.tryLock()) {
            return;
        }
        try {
            List<SkillUsageRecord> records = usageRepository.findByAgentAndSkill(agentKey, skillName);
            List<SkillUsageRecord> validRecords = new ArrayList<>();
            List<String> invalidIds = new ArrayList<>();

            for (SkillUsageRecord record : records) {
                SkillUsageValidationResult validation = messageCollector.validate(record);
                if (validation.isValid()) {
                    validRecords.add(record);
                } else {
                    invalidIds.add(record.getId());
                }
            }

            if (!invalidIds.isEmpty()) {
                usageRepository.deleteByIds(invalidIds);
            }
            if (validRecords.size() < properties.getUsageThreshold()) {
                return;
            }

            List<SkillConversationSlice> slices = messageCollector.collectValidSlices(validRecords);
            if (slices.isEmpty()) {
                return;
            }

            String existingExperience = experienceRepository.find(agentKey, skillName)
                    .map(SkillExperienceMemory::getContent)
                    .orElse("");
            String rewritten = extractor.regenerate(agentKey, skillName, existingExperience, slices);
            experienceRepository.upsert(agentKey, skillName, rewritten);
            usageRepository.deleteByIds(validRecords.stream().map(SkillUsageRecord::getId).toList());
        } finally {
            lock.unlock();
        }
    }
}
