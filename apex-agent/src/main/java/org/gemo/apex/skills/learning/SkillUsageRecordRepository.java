package org.gemo.apex.skills.learning;

import org.gemo.apex.skills.learning.model.SkillUsageRecord;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SkillUsageRecordRepository {

    void insert(SkillUsageRecord record);

    Map<String, Integer> countByAgentAndSkill();

    List<SkillUsageRecord> findByAgentAndSkill(String agentKey, String skillName);

    void deleteByIds(Collection<String> ids);
}
