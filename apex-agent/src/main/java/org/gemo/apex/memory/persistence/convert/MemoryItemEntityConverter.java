package org.gemo.apex.memory.persistence.convert;

import org.gemo.apex.memory.model.MemoryCategory;
import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.model.MemoryStatus;
import org.gemo.apex.memory.model.MemoryType;
import org.gemo.apex.memory.model.ExecutionTimeScope;
import org.gemo.apex.memory.persistence.entity.AgentExperienceMemoryEntity;
import org.gemo.apex.memory.persistence.entity.UserExecutionHistoryMemoryEntity;
import org.gemo.apex.memory.persistence.entity.UserProfileMemoryEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 逻辑记忆对象与实体之间的转换器。
 */
public final class MemoryItemEntityConverter {

    private MemoryItemEntityConverter() {
    }

    public static UserProfileMemoryEntity toProfileEntity(MemoryItem item) {
        UserProfileMemoryEntity entity = new UserProfileMemoryEntity();
        entity.setId(item.getId());
        entity.setUserId(item.getUserId());
        entity.setAgentKey(item.getAgentKey());
        entity.setMemoryKey(item.getMemoryKey());
        entity.setMemoryCategory(item.getMemoryCategory() != null ? item.getMemoryCategory().name() : null);
        entity.setTitle(item.getTitle());
        entity.setContent(item.getContent());
        entity.setStructuredPayload(item.getStructuredPayload());
        entity.setConfidence(toDecimal(item.getConfidence()));
        entity.setImportance(item.getImportance());
        entity.setStatus(item.getStatus() != null ? item.getStatus().name() : MemoryStatus.ACTIVE.name());
        entity.setSourceSessionId(item.getSourceSessionId());
        entity.setObservedTime(defaultTime(item.getObservedTime()));
        entity.setExpireTime(item.getExpireTime());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    public static UserExecutionHistoryMemoryEntity toExecutionEntity(MemoryItem item) {
        UserExecutionHistoryMemoryEntity entity = new UserExecutionHistoryMemoryEntity();
        entity.setId(item.getId());
        entity.setUserId(item.getUserId());
        entity.setAgentKey(item.getAgentKey());
        entity.setTopicKey(item.getTopicKey());
        entity.setTitle(item.getTitle());
        entity.setContent(item.getContent());
        entity.setTimeScope(item.getTimeScope() != null ? item.getTimeScope().name() : null);
        entity.setStartTime(item.getStartTime());
        entity.setEndTime(item.getEndTime());
        entity.setStructuredPayload(item.getStructuredPayload());
        entity.setConfidence(toDecimal(item.getConfidence()));
        entity.setLastTurnNo(item.getTurnNo());
        entity.setVersionNo(item.getVersionNo());
        entity.setStatus(item.getStatus() != null ? item.getStatus().name() : MemoryStatus.ACTIVE.name());
        entity.setSourceSessionId(item.getSourceSessionId());
        entity.setObservedTime(defaultTime(item.getObservedTime()));
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    public static AgentExperienceMemoryEntity toExperienceEntity(MemoryItem item) {
        AgentExperienceMemoryEntity entity = new AgentExperienceMemoryEntity();
        entity.setId(item.getId());
        entity.setAgentKey(item.getAgentKey());
        entity.setTopicKey(item.getTopicKey());
        entity.setMemoryKey(item.getMemoryKey());
        entity.setTitle(item.getTitle());
        entity.setContent(item.getContent());
        entity.setStructuredPayload(item.getStructuredPayload());
        entity.setConfidence(toDecimal(item.getConfidence()));
        entity.setStatus(item.getStatus() != null ? item.getStatus().name() : MemoryStatus.ACTIVE.name());
        entity.setSourceSessionId(item.getSourceSessionId());
        entity.setObservedTime(defaultTime(item.getObservedTime()));
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    public static MemoryItem fromProfileEntity(UserProfileMemoryEntity entity) {
        MemoryItem item = new MemoryItem();
        item.setId(entity.getId());
        item.setUserId(entity.getUserId());
        item.setAgentKey(entity.getAgentKey());
        item.setType("FACT".equalsIgnoreCase(entity.getMemoryCategory()) ? MemoryType.FACT : MemoryType.LONG_TERM);
        item.setMemoryCategory(entity.getMemoryCategory() != null ? MemoryCategory.valueOf(entity.getMemoryCategory()) : null);
        item.setMemoryKey(entity.getMemoryKey());
        item.setTitle(entity.getTitle());
        item.setContent(entity.getContent());
        item.setStructuredPayload(entity.getStructuredPayload());
        item.setConfidence(toDouble(entity.getConfidence()));
        item.setImportance(entity.getImportance());
        item.setStatus(entity.getStatus() != null ? MemoryStatus.valueOf(entity.getStatus()) : MemoryStatus.ACTIVE);
        item.setSourceSessionId(entity.getSourceSessionId());
        item.setObservedTime(entity.getObservedTime());
        item.setExpireTime(entity.getExpireTime());
        return item;
    }

    public static MemoryItem fromExecutionEntity(UserExecutionHistoryMemoryEntity entity) {
        MemoryItem item = new MemoryItem();
        item.setId(entity.getId());
        item.setUserId(entity.getUserId());
        item.setAgentKey(entity.getAgentKey());
        item.setType(MemoryType.EXECUTION_HISTORY);
        item.setTopicKey(entity.getTopicKey());
        item.setTitle(entity.getTitle());
        item.setContent(entity.getContent());
        item.setTimeScope(entity.getTimeScope() != null ? ExecutionTimeScope.valueOf(entity.getTimeScope()) : null);
        item.setStartTime(entity.getStartTime());
        item.setEndTime(entity.getEndTime());
        item.setStructuredPayload(entity.getStructuredPayload());
        item.setConfidence(toDouble(entity.getConfidence()));
        item.setTurnNo(entity.getLastTurnNo());
        item.setVersionNo(entity.getVersionNo());
        item.setStatus(entity.getStatus() != null ? MemoryStatus.valueOf(entity.getStatus()) : MemoryStatus.ACTIVE);
        item.setSourceSessionId(entity.getSourceSessionId());
        item.setObservedTime(entity.getObservedTime());
        return item;
    }

    public static MemoryItem fromExperienceEntity(AgentExperienceMemoryEntity entity) {
        MemoryItem item = new MemoryItem();
        item.setId(entity.getId());
        item.setAgentKey(entity.getAgentKey());
        item.setType(MemoryType.AGENT_EXPERIENCE);
        item.setTopicKey(entity.getTopicKey());
        item.setMemoryKey(entity.getMemoryKey());
        item.setTitle(entity.getTitle());
        item.setContent(entity.getContent());
        item.setStructuredPayload(entity.getStructuredPayload());
        item.setConfidence(toDouble(entity.getConfidence()));
        item.setStatus(entity.getStatus() != null ? MemoryStatus.valueOf(entity.getStatus()) : MemoryStatus.ACTIVE);
        item.setSourceSessionId(entity.getSourceSessionId());
        item.setObservedTime(entity.getObservedTime());
        return item;
    }

    private static BigDecimal toDecimal(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private static Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private static LocalDateTime defaultTime(LocalDateTime time) {
        return time != null ? time : LocalDateTime.now();
    }
}
