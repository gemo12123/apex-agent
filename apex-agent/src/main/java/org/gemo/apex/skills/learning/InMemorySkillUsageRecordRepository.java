package org.gemo.apex.skills.learning;

import org.gemo.apex.skills.learning.model.SkillUsageRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "apex.memory.store", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemorySkillUsageRecordRepository implements SkillUsageRecordRepository {

    private final Map<String, SkillUsageRecord> storage = new ConcurrentHashMap<>();

    @Override
    public void insert(SkillUsageRecord record) {
        storage.put(record.getId(), record);
    }

    @Override
    public Map<String, Integer> countByAgentAndSkill() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SkillUsageRecord record : storage.values()) {
            String key = record.getAgentKey() + "::" + record.getSkillName();
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }

    @Override
    public List<SkillUsageRecord> findByAgentAndSkill(String agentKey, String skillName) {
        return storage.values().stream()
                .filter(record -> Objects.equals(agentKey, record.getAgentKey()))
                .filter(record -> Objects.equals(skillName, record.getSkillName()))
                .sorted((left, right) -> {
                    if (left.getCreatedTime() == null && right.getCreatedTime() == null) {
                        return 0;
                    }
                    if (left.getCreatedTime() == null) {
                        return -1;
                    }
                    if (right.getCreatedTime() == null) {
                        return 1;
                    }
                    return left.getCreatedTime().compareTo(right.getCreatedTime());
                })
                .toList();
    }

    @Override
    public void deleteByIds(Collection<String> ids) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            storage.remove(id);
        }
    }
}
