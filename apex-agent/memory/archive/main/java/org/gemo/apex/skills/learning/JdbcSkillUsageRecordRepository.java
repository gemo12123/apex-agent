package org.gemo.apex.skills.learning;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.gemo.apex.skills.learning.model.SkillUsageRecord;
import org.gemo.apex.skills.learning.persistence.entity.SkillUsageRecordEntity;
import org.gemo.apex.skills.learning.persistence.mapper.SkillUsageRecordMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "apex.memory.store", name = "type", havingValue = "jdbc")
public class JdbcSkillUsageRecordRepository implements SkillUsageRecordRepository {

    private final SkillUsageRecordMapper mapper;

    public JdbcSkillUsageRecordRepository(SkillUsageRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(SkillUsageRecord record) {
        mapper.insert(toEntity(record));
    }

    @Override
    public Map<String, Integer> countByAgentAndSkill() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SkillUsageRecordEntity entity : mapper.selectList(new LambdaQueryWrapper<>())) {
            String key = entity.getAgentKey() + "::" + entity.getSkillName();
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }

    @Override
    public List<SkillUsageRecord> findByAgentAndSkill(String agentKey, String skillName) {
        return mapper.selectList(new LambdaQueryWrapper<SkillUsageRecordEntity>()
                        .eq(SkillUsageRecordEntity::getAgentKey, agentKey)
                        .eq(SkillUsageRecordEntity::getSkillName, skillName)
                        .orderByAsc(SkillUsageRecordEntity::getCreatedTime))
                .stream()
                .map(this::toModel)
                .toList();
    }

    @Override
    public void deleteByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        mapper.deleteBatchIds(ids);
    }

    private SkillUsageRecordEntity toEntity(SkillUsageRecord record) {
        SkillUsageRecordEntity entity = new SkillUsageRecordEntity();
        entity.setId(record.getId() != null ? record.getId() : IdUtil.simpleUUID());
        entity.setAgentKey(record.getAgentKey());
        entity.setSkillName(record.getSkillName());
        entity.setSessionId(record.getSessionId());
        entity.setTurnNo(record.getTurnNo());
        entity.setActivationMessageSortNo(record.getActivationMessageSortNo());
        entity.setCreatedTime(record.getCreatedTime() != null ? record.getCreatedTime() : LocalDateTime.now());
        return entity;
    }

    private SkillUsageRecord toModel(SkillUsageRecordEntity entity) {
        return SkillUsageRecord.builder()
                .id(entity.getId())
                .agentKey(entity.getAgentKey())
                .skillName(entity.getSkillName())
                .sessionId(entity.getSessionId())
                .turnNo(entity.getTurnNo())
                .activationMessageSortNo(entity.getActivationMessageSortNo())
                .createdTime(entity.getCreatedTime())
                .build();
    }
}
