package org.gemo.apex.skills.learning;

import cn.hutool.core.util.IdUtil;
import org.gemo.apex.skills.learning.model.SkillExperienceMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "apex.memory.store", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemorySkillExperienceMemoryRepository implements SkillExperienceMemoryRepository {

    private final Map<String, SkillExperienceMemory> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<SkillExperienceMemory> find(String agentKey, String skillName) {
        return Optional.ofNullable(storage.get(key(agentKey, skillName)));
    }

    @Override
    public SkillExperienceMemory upsert(String agentKey, String skillName, String content) {
        SkillExperienceMemory existing = storage.get(key(agentKey, skillName));
        LocalDateTime now = LocalDateTime.now();
        SkillExperienceMemory updated = SkillExperienceMemory.builder()
                .id(existing != null ? existing.getId() : IdUtil.simpleUUID())
                .agentKey(agentKey)
                .skillName(skillName)
                .content(content)
                .versionNo(existing != null ? existing.getVersionNo() + 1L : 1L)
                .createTime(existing != null ? existing.getCreateTime() : now)
                .updateTime(now)
                .build();
        storage.put(key(agentKey, skillName), updated);
        return updated;
    }

    private String key(String agentKey, String skillName) {
        return agentKey + "::" + skillName;
    }
}
