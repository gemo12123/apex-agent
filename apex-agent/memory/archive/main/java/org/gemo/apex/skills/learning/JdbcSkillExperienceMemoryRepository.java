package org.gemo.apex.skills.learning;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.gemo.apex.skills.learning.model.SkillExperienceMemory;
import org.gemo.apex.skills.learning.persistence.entity.SkillExperienceMemoryEntity;
import org.gemo.apex.skills.learning.persistence.mapper.SkillExperienceMemoryMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "apex.memory.store", name = "type", havingValue = "jdbc")
public class JdbcSkillExperienceMemoryRepository implements SkillExperienceMemoryRepository {

    private final SkillExperienceMemoryMapper mapper;

    public JdbcSkillExperienceMemoryRepository(SkillExperienceMemoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<SkillExperienceMemory> find(String agentKey, String skillName) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<SkillExperienceMemoryEntity>()
                        .eq(SkillExperienceMemoryEntity::getAgentKey, agentKey)
                        .eq(SkillExperienceMemoryEntity::getSkillName, skillName)
                        .last("LIMIT 1")))
                .map(this::toModel);
    }

    @Override
    public SkillExperienceMemory upsert(String agentKey, String skillName, String content) {
        SkillExperienceMemoryEntity existing = mapper.selectOne(new LambdaQueryWrapper<SkillExperienceMemoryEntity>()
                .eq(SkillExperienceMemoryEntity::getAgentKey, agentKey)
                .eq(SkillExperienceMemoryEntity::getSkillName, skillName)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        SkillExperienceMemoryEntity entity = new SkillExperienceMemoryEntity();
        entity.setId(existing != null ? existing.getId() : IdUtil.simpleUUID());
        entity.setAgentKey(agentKey);
        entity.setSkillName(skillName);
        entity.setContent(content);
        entity.setVersionNo(existing != null ? existing.getVersionNo() + 1L : 1L);
        entity.setCreateTime(existing != null ? existing.getCreateTime() : now);
        entity.setUpdateTime(now);
        if (existing == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return toModel(entity);
    }

    private SkillExperienceMemory toModel(SkillExperienceMemoryEntity entity) {
        return SkillExperienceMemory.builder()
                .id(entity.getId())
                .agentKey(entity.getAgentKey())
                .skillName(entity.getSkillName())
                .content(entity.getContent())
                .versionNo(entity.getVersionNo())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
